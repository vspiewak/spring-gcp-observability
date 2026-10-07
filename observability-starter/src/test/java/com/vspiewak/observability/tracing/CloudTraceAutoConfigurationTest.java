package com.vspiewak.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

class CloudTraceAutoConfigurationTest {

  private static final GoogleCredentials CREDENTIALS =
      GoogleCredentials.create(
          new AccessToken("t0k3n", Date.from(Instant.now().plusSeconds(3600))));

  /**
   * Spring Cloud GCP's credentials, faked : no Google account involved. The build's own environment
   * is left out, so an exported SPRING_CLOUD_GCP_PROJECT_ID cannot leak in.
   */
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
              })
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
  @SuppressWarnings("unchecked")
  void shouldHandTheExporterATokenToFetchOnEveryExport() {
    runner
        .withPropertyValues("spring.cloud.gcp.project-id=demo-project")
        .run(
            context -> {
              // given
              var builder = mock(OtlpHttpSpanExporterBuilder.class);
              ArgumentCaptor<Supplier<Map<String, String>>> headers =
                  ArgumentCaptor.forClass(Supplier.class);

              // when
              context.getBean(OtlpHttpSpanExporterBuilderCustomizer.class).customize(builder);

              // then : a supplier the exporter calls on every export — not a header fixed once
              verify(builder).setHeaders(headers.capture());
              assertThat(headers.getValue().get()).containsEntry("Authorization", "Bearer t0k3n");
            });
  }

  @Test
  void shouldSendTheAccessTokenAsABearer() {
    assertThat(CloudTraceAutoConfiguration.authorization(CREDENTIALS))
        .containsEntry("Authorization", "Bearer t0k3n");
  }
}
