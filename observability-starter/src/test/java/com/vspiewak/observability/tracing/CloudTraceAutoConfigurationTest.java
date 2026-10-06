package com.vspiewak.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudTraceAutoConfigurationTest {

  private static final GoogleCredentials CREDENTIALS =
      GoogleCredentials.create(
          new AccessToken("t0k3n", Date.from(Instant.now().plusSeconds(3600))));

  /** Spring Cloud GCP's credentials, faked : no Google account involved. */
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CloudTraceAutoConfiguration.class, OtlpTracingAutoConfiguration.class))
          .withBean(CredentialsProvider.class, () -> () -> CREDENTIALS);

  @Test
  void shouldStayOutOfTheWayWithoutAGoogleCloudProject() {
    runner.run(
        context ->
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporterBuilderCustomizer.class));
  }

  @Test
  void shouldSignTheExportOnceTheProjectIsKnown() {
    runner
        .withPropertyValues(
            "spring.cloud.gcp.project-id=demo-project",
            "management.opentelemetry.tracing.export.otlp.endpoint=https://telemetry.googleapis.com/v1/traces")
        .run(
            context -> {
              assertThat(context).hasSingleBean(OtlpHttpSpanExporterBuilderCustomizer.class);
              // Boot's own exporter is built with it — no token fetched until the first export
              assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
            });
  }

  @Test
  void shouldSendTheAccessTokenAsABearer() {
    assertThat(CloudTraceAutoConfiguration.authorization(CREDENTIALS))
        .containsEntry("Authorization", "Bearer t0k3n");
  }
}
