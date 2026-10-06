package com.vspiewak.observability.mongo;

import com.mongodb.observability.micrometer.MicrometerObservabilitySettings;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Hands the {@code ObservationRegistry} to the MongoDB driver's own tracing (5.7+), query values
 * left out.
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
@ConditionalOnClass({
  MicrometerObservabilitySettings.class,
  MongoClientSettingsBuilderCustomizer.class,
  ObservationRegistry.class
})
@ConditionalOnBean(ObservationRegistry.class)
public class MongoTracingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "mongoTracingCustomizer")
  public MongoClientSettingsBuilderCustomizer mongoTracingCustomizer(
      ObservationRegistry observationRegistry) {
    return clientSettingsBuilder ->
        clientSettingsBuilder.observabilitySettings(
            MicrometerObservabilitySettings.builder()
                .observationRegistry(observationRegistry)
                .build());
  }
}
