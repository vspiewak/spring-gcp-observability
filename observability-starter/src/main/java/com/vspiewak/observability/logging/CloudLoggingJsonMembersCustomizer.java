package com.vspiewak.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Adds the three fields Google Cloud Logging reads to Boot's JSON log lines : {@code severity}, and
 * the line's trace and span — so each line lands under its trace, and on its span.
 */
public class CloudLoggingJsonMembersCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

  private final @Nullable String tracePrefix;

  public CloudLoggingJsonMembersCustomizer(Environment environment) {
    String projectId = environment.getProperty("spring.cloud.gcp.project-id");
    this.tracePrefix = StringUtils.hasText(projectId) ? "projects/" + projectId + "/traces/" : null;
  }

  @Override
  public void customize(Members<ILoggingEvent> members) {
    if (this.tracePrefix == null) {
      return; // JSON logs without a project : nothing Google-specific to add
    }
    members
        .add("severity", ILoggingEvent::getLevel)
        .as(CloudLoggingJsonMembersCustomizer::severity);
    members
        .add("logging.googleapis.com/trace", (event) -> event.getMDCPropertyMap().get("traceId"))
        .whenHasLength()
        .as(this.tracePrefix::concat);
    members
        .add("logging.googleapis.com/spanId", (event) -> event.getMDCPropertyMap().get("spanId"))
        .whenHasLength();
  }

  static String severity(Level level) {
    return (level == Level.WARN) ? "WARNING" : level.toString();
  }
}
