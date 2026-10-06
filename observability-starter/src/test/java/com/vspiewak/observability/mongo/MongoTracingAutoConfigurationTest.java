package com.vspiewak.observability.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.MongoClientSettings;
import com.mongodb.observability.micrometer.MicrometerObservabilitySettings;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MongoTracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MongoTracingAutoConfiguration.class))
          .withBean(ObservationRegistry.class, ObservationRegistry::create);

  @Test
  void shouldHandTheObservationRegistryToTheDriver() {
    runner.run(
        context -> {
          // given
          var builder = MongoClientSettings.builder();

          // when
          context.getBean(MongoClientSettingsBuilderCustomizer.class).customize(builder);

          // then
          assertThat(builder.build().getObservabilitySettings())
              .isInstanceOfSatisfying(
                  MicrometerObservabilitySettings.class,
                  settings -> {
                    assertThat(settings.getObservationRegistry())
                        .isSameAs(context.getBean(ObservationRegistry.class));
                    assertThat(settings.isEnableCommandPayloadTracing()).isFalse();
                  });
        });
  }

  @Test
  void shouldBackOffWhenTheServiceDefinesItsOwnCustomizer() {
    runner
        .withBean(
            "mongoTracingCustomizer",
            MongoClientSettingsBuilderCustomizer.class,
            () -> clientSettingsBuilder -> {})
        .run(
            context -> {
              // given
              var builder = MongoClientSettings.builder();

              // when
              context.getBean(MongoClientSettingsBuilderCustomizer.class).customize(builder);

              // then
              assertThat(builder.build().getObservabilitySettings()).isNull();
            });
  }
}
