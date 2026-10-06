package com.vspiewak.observability.tracing;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.util.function.SingletonSupplier;

/**
 * Signs every trace export with a Google access token. Boot's OTLP exporter only sends fixed
 * headers, and a token expires : the headers are read again on every export.
 */
@AutoConfiguration
@ConditionalOnProperty("GOOGLE_CLOUD_PROJECT")
public class CloudTraceAutoConfiguration {

  @Bean
  public OtlpHttpSpanExporterBuilderCustomizer googleCloudAuthentication() {
    Supplier<GoogleCredentials> credentials =
        SingletonSupplier.of(CloudTraceAutoConfiguration::applicationDefaultCredentials);
    return builder -> builder.setHeaders(() -> authorization(credentials.get()));
  }

  static Map<String, String> authorization(GoogleCredentials credentials) {
    try {
      Map<String, String> headers = new HashMap<>();
      credentials
          .getRequestMetadata()
          .forEach((name, values) -> headers.put(name, String.join(",", values)));
      return headers;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static GoogleCredentials applicationDefaultCredentials() {
    try {
      return GoogleCredentials.getApplicationDefault()
          .createScoped("https://www.googleapis.com/auth/cloud-platform");
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
