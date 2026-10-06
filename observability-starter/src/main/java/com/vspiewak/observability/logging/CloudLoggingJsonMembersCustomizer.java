package com.vspiewak.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Renames Boot's structured JSON for Google Cloud Logging — {@code severity}, {@code time}, the
 * trace and span under {@code logging.googleapis.com/*} — once the project is known.
 *
 * <p>Registered in {@code spring.factories} : a service setting {@code
 * logging.structured.json.customizer} itself would otherwise replace it.
 */
public class CloudLoggingJsonMembersCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

  static final String SEVERITY = "severity";
  static final String TRACE = "logging.googleapis.com/trace";
  static final String SPAN_ID = "logging.googleapis.com/spanId";

  /** Logstash fields Google names differently, or ignores. */
  private static final Set<String> DROPPED = Set.of("level", "level_value", "@version");

  private final @Nullable String projectId;

  public CloudLoggingJsonMembersCustomizer(Environment environment) {
    this.projectId = environment.getProperty("spring.cloud.gcp.project-id");
  }

  @Override
  public void customize(Members<ILoggingEvent> members) {
    if (!StringUtils.hasText(this.projectId)) {
      return;
    }
    members.add(SEVERITY, ILoggingEvent::getLevel).as(CloudLoggingJsonMembersCustomizer::severity);
    members
        .add(TRACE, (event) -> mdc(event, "traceId"))
        .whenHasLength()
        .as((traceId) -> "projects/%s/traces/%s".formatted(this.projectId, traceId));
    members.add(SPAN_ID, (event) -> mdc(event, "spanId")).whenHasLength();
    members.applyingPathFilter((path) -> DROPPED.contains(path.toUnescapedString()));
    // an RFC 3339 "time" is the timestamp Cloud Logging reads ; "@timestamp" would land in the
    // payload
    members.applyingNameProcessor(
        (path, name) -> "@timestamp".equals(path.toUnescapedString()) ? "time" : name);
  }

  static String severity(Level level) {
    return switch (level.toInt()) {
      case Level.ERROR_INT -> "ERROR";
      case Level.WARN_INT -> "WARNING";
      case Level.INFO_INT -> "INFO";
      default -> "DEBUG";
    };
  }

  private static @Nullable String mdc(ILoggingEvent event, String key) {
    return event.getMDCPropertyMap().get(key);
  }
}
