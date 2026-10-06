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

  @Test
  void shouldNameTheProjectOnTheSpansOnceItIsKnown() {
    // when
    var environment = boot("--spring.cloud.gcp.project-id=demo-project");

    // then
    assertThat(
            environment.getProperty("management.opentelemetry.resource-attributes[gcp.project_id]"))
        .isEqualTo("demo-project");
  }
}
