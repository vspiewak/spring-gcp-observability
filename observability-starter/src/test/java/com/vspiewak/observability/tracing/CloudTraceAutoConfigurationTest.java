package com.vspiewak.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudTraceAutoConfigurationTest {

  /** Ours, next to Boot's OTLP exporter auto-configuration it feeds. */
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CloudTraceAutoConfiguration.class, OtlpTracingAutoConfiguration.class));

  @Test
  void shouldStayLocalWithoutAGoogleCloudProject() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(OtlpTracingConnectionDetails.class);
          assertThat(context).doesNotHaveBean(GoogleCloudOtlpAuthCustomizer.class);
          assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
        });
  }

  @Test
  void shouldExportToCloudTraceOnceTheProjectIsKnown() {
    runner
        .withPropertyValues("spring.cloud.gcp.project-id=demo-project")
        .run(
            context -> {
              assertThat(context.getBean(OtlpTracingConnectionDetails.class).getUrl(Transport.HTTP))
                  .isEqualTo("https://telemetry.googleapis.com/v1/traces");
              assertThat(context).hasSingleBean(GoogleCloudOtlpAuthCustomizer.class);
              // Boot's own exporter picked the endpoint up : the ordering holds
              assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
            });
  }

  @Test
  void shouldLetTheServicePointTheExportElsewhere() {
    runner
        .withPropertyValues(
            "spring.cloud.gcp.project-id=demo-project",
            "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces")
        .run(
            context ->
                assertThat(
                        context.getBean(OtlpTracingConnectionDetails.class).getUrl(Transport.HTTP))
                    .isEqualTo("http://localhost:4318/v1/traces"));
  }

  @Test
  void shouldBeSwitchedOffByProperty() {
    runner
        .withPropertyValues(
            "spring.cloud.gcp.project-id=demo-project", "platform.cloud-trace.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(GoogleCloudOtlpAuthCustomizer.class);
              assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
            });
  }
}
