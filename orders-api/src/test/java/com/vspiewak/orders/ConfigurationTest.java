package com.vspiewak.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * orders-api's configuration as Spring resolves its {@code application*.yaml} : on a laptop with no
 * profile, and on Cloud Run with the environment variables Terraform sets. Nothing is started.
 */
class ConfigurationTest {

  /** The configuration, against these environment variables and no others — not the build's own. */
  private static ApplicationContextRunner withEnvironment(Map<String, Object> variables) {
    return new ApplicationContextRunner()
        .withInitializer(
            context -> {
              var sources = context.getEnvironment().getPropertySources();
              sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
              sources.replace(
                  StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                  new SystemEnvironmentPropertySource(
                      StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
            })
        .withInitializer(new ConfigDataApplicationContextInitializer());
  }

  @Test
  void shouldRunOnALaptopWithoutAnyProfile() {
    withEnvironment(Map.of())
        .run(
            context -> {
              var environment = context.getEnvironment();
              // the MongoDB of compose.yaml, pricing-api next door
              assertThat(environment.getProperty("spring.mongodb.uri"))
                  .isEqualTo("mongodb://localhost:27017");
              assertThat(environment.getProperty("spring.mongodb.database")).isEqualTo("orders");
              assertThat(environment.getProperty("pricing.url")).isEqualTo("http://localhost:8081");
            });
  }

  @Test
  void shouldReachAtlasAndPricingApiOnCloudRun() {
    // the environment terraform/run.tf gives orders-api
    withEnvironment(
            Map.of(
                "SPRING_PROFILES_ACTIVE", "gcp",
                "SPRING_CLOUD_GCP_PROJECT_ID", "demo-project",
                "MONGODB_HOST", "cluster0.example.mongodb.net",
                "MONGODB_PASSWORD", "s3cr3t",
                "PRICING_URL", "https://pricing-api.example.run.app"))
        .run(
            context -> {
              var environment = context.getEnvironment();
              assertThat(environment.getProperty("spring.mongodb.uri"))
                  .isEqualTo(
                      "mongodb+srv://orders-api:s3cr3t@cluster0.example.mongodb.net/?retryWrites=true&w=majority");
              // the Atlas user may only write there
              assertThat(environment.getProperty("spring.mongodb.database")).isEqualTo("orders");
              assertThat(environment.getProperty("pricing.url"))
                  .isEqualTo("https://pricing-api.example.run.app");
            });
  }
}
