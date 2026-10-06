package com.vspiewak.observability.tracing;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.util.function.SingletonSupplier;

/**
 * Signs every trace export with a Google access token, from Spring Cloud GCP's credentials. Boot's
 * OTLP exporter only sends fixed headers, and a token expires : the headers are read again on every
 * export.
 */
@AutoConfiguration(
    afterName = "com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration")
@ConditionalOnProperty("spring.cloud.gcp.project-id")
@ConditionalOnBean(CredentialsProvider.class)
public class CloudTraceAutoConfiguration {

  @Bean
  public OtlpHttpSpanExporterBuilderCustomizer googleCloudAuthentication(
      CredentialsProvider credentialsProvider) {
    Supplier<Credentials> credentials =
        SingletonSupplier.of(() -> credentials(credentialsProvider));
    return builder -> builder.setHeaders(() -> authorization(credentials.get()));
  }

  static Map<String, String> authorization(Credentials credentials) {
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

  private static Credentials credentials(CredentialsProvider credentialsProvider) {
    try {
      return credentialsProvider.getCredentials();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
