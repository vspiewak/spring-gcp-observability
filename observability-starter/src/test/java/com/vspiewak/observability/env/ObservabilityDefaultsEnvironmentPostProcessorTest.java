package com.vspiewak.observability.env;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

/** Boots a real application : the defaults have to sit below a real {@code application.yaml}. */
class ObservabilityDefaultsEnvironmentPostProcessorTest {

  @SpringBootConfiguration
  static class NoBeans {}

  private static Environment boot(String... args) {
    try (var context =
        new SpringApplicationBuilder(NoBeans.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .run(args)) {
      return context.getEnvironment();
    }
  }

  @Test
  void shouldGiveEveryServiceThePlatformDefaults() {
    // when
    var environment = boot();

    // then
    assertThat(environment.getProperty("management.opentelemetry.tracing.sampler"))
        .isEqualTo("trace-id-ratio");
    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
    assertThat(environment.getProperty("management.observations.annotations.enabled"))
        .isEqualTo("true");
    assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
        .isEqualTo("false");
  }

  @Test
  void shouldStayLocalWithoutAGoogleCloudProject() {
    // when
    var environment = boot();

    // then
    assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
        .isNull();
    assertThat(environment.getProperty("logging.structured.format.console")).isNull();
  }

  @Test
  void shouldTurnGoogleCloudOnWithTheProject() {
    // when
    var environment = boot("--GOOGLE_CLOUD_PROJECT=demo-project");

    // then
    assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
        .isEqualTo("https://telemetry.googleapis.com/v1/traces");
    assertThat(
            environment.getProperty("management.opentelemetry.resource-attributes.gcp.project_id"))
        .isEqualTo("demo-project");
    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
  }

  @Test
  void shouldLetTheServicesOwnApplicationYamlWin() {
    // when : service-overrides.yaml plays the service's application.yaml
    var environment = boot("--spring.config.name=service-overrides");

    // then
    assertThat(environment.getProperty("management.opentelemetry.tracing.sampler"))
        .isEqualTo("parent-based-trace-id-ratio");
    assertThat(environment.getProperty("management.tracing.sampling.probability"))
        .isEqualTo("0.25");
  }
}
