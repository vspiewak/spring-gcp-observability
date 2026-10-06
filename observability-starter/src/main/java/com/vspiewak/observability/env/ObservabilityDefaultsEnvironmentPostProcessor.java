package com.vspiewak.observability.env;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

/**
 * Loads the platform defaults below everything else, so a service's own configuration wins : {@code
 * observability-defaults.yaml} always, {@code observability-gcp.yaml} once {@code
 * GOOGLE_CLOUD_PROJECT} says the service runs on Google Cloud.
 */
public class ObservabilityDefaultsEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  /** After config data : {@code application.yaml} must be in place to go below it. */
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    load(environment, "observability-defaults");
    if (StringUtils.hasText(environment.getProperty("GOOGLE_CLOUD_PROJECT"))) {
      load(environment, "observability-gcp");
    }
  }

  private static void load(ConfigurableEnvironment environment, String name) {
    try {
      new YamlPropertySourceLoader()
          .load(name, new ClassPathResource(name + ".yaml"))
          .forEach(environment.getPropertySources()::addLast);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
