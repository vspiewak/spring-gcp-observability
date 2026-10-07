package com.vspiewak.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Environment;

/**
 * orders-api's configuration, resolved without starting anything : on a laptop with no profile, and
 * under the {@code gcp} profile with the variables Terraform sets on Cloud Run.
 */
class ConfigurationTest {

  /** No beans, no auto-configuration : only the environment gets built. */
  static class NoBeans {}

  private static Environment resolve(String profile, String... environment) {
    var application =
        new SpringApplicationBuilder(NoBeans.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false);
    if (profile != null) {
      application.profiles(profile);
    }
    try (var context = application.run(environment)) {
      return context.getEnvironment();
    }
  }

  @Test
  void shouldRunOnALaptopWithoutAnyProfile() {
    // when
    var environment = resolve(null);

    // then : the MongoDB of compose.yaml, pricing-api next door
    assertThat(environment.getProperty("spring.mongodb.uri"))
        .isEqualTo("mongodb://localhost:27017");
    assertThat(environment.getProperty("pricing.url")).isEqualTo("http://localhost:8081");
  }

  @Test
  void shouldReachAtlasAndPricingApiUnderTheGcpProfile() {
    // when : the variables terraform/run.tf sets
    var environment =
        resolve(
            "gcp",
            "--MONGODB_HOST=cluster0.example.mongodb.net",
            "--MONGODB_PASSWORD=s3cr3t",
            "--PRICING_URL=https://pricing-api.example.run.app");

    // then
    assertThat(environment.getProperty("spring.mongodb.uri"))
        .isEqualTo(
            "mongodb+srv://orders-api:s3cr3t@cluster0.example.mongodb.net/?retryWrites=true&w=majority");
    assertThat(environment.getProperty("pricing.url"))
        .isEqualTo("https://pricing-api.example.run.app");
  }
}
