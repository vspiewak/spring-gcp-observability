package com.vspiewak.observability.tracing;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Exports the traces to Cloud Trace once the project is known ({@code spring.cloud.gcp.project-id})
 * — without it, nothing leaves the machine. Off with {@code platform.cloud-trace.enabled=false}.
 */
@AutoConfiguration(
    beforeName =
        "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration")
@ConditionalOnClass({OtlpHttpSpanExporterBuilder.class, GoogleCredentials.class})
@ConditionalOnProperty("spring.cloud.gcp.project-id")
@ConditionalOnBooleanProperty(name = "platform.cloud-trace.enabled", matchIfMissing = true)
public class CloudTraceAutoConfiguration {

  static final String TELEMETRY_API = "https://telemetry.googleapis.com/v1/traces";

  /** Google's OTLP endpoint, unless the service points the export elsewhere. */
  @Bean
  @ConditionalOnMissingBean
  public OtlpTracingConnectionDetails cloudTraceConnectionDetails(Environment environment) {
    String endpoint =
        environment.getProperty(
            "management.opentelemetry.tracing.export.otlp.endpoint", TELEMETRY_API);
    return transport -> endpoint;
  }

  @Bean
  @ConditionalOnMissingBean
  public GoogleCloudOtlpAuthCustomizer googleCloudOtlpAuthCustomizer(
      ObjectProvider<CredentialsProvider> credentialsProvider,
      OtlpTracingConnectionDetails connectionDetails) {
    return new GoogleCloudOtlpAuthCustomizer(
        () -> credentials(credentialsProvider),
        URI.create(connectionDetails.getUrl(Transport.HTTP)));
  }

  /** Spring Cloud GCP's credentials when configured, the Application Default Credentials else. */
  private static Credentials credentials(ObjectProvider<CredentialsProvider> credentialsProvider) {
    try {
      CredentialsProvider configured = credentialsProvider.getIfAvailable();
      return (configured != null)
          ? configured.getCredentials()
          : GoogleCredentials.getApplicationDefault()
              .createScoped("https://www.googleapis.com/auth/cloud-platform");
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
