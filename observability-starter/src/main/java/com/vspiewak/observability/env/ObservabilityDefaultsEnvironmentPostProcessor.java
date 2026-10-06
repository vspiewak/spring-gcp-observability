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

/**
 * Loads {@code observability-defaults.yaml} below everything else : a service's own configuration
 * wins over any of these defaults.
 */
public class ObservabilityDefaultsEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  private static final String NAME = "observability-defaults";

  /** After config data : {@code application.yaml} must be in place to go below it. */
  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    try {
      new YamlPropertySourceLoader()
          .load(NAME, new ClassPathResource(NAME + ".yaml"))
          .forEach(environment.getPropertySources()::addLast);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
