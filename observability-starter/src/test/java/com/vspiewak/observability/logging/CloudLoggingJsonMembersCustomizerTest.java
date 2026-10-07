package com.vspiewak.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;
import org.springframework.mock.env.MockEnvironment;

class CloudLoggingJsonMembersCustomizerTest {

  private final CloudLoggingJsonMembersCustomizer customizer =
      new CloudLoggingJsonMembersCustomizer(
          new MockEnvironment().withProperty("spring.cloud.gcp.project-id", "demo-project"));

  private String format(ILoggingEvent event) {
    JsonWriter<ILoggingEvent> writer =
        JsonWriter.of(
            members -> {
              members.add("message", ILoggingEvent::getFormattedMessage);
              customizer.customize(members);
            });
    return writer.writeToString(event);
  }

  private static ILoggingEvent event(Level level, Map<String, String> mdc) {
    var logger = new LoggerContext().getLogger("com.example.OrderService");
    var event = new LoggingEvent(null, logger, level, "found order 42", null, null);
    event.setMDCPropertyMap(mdc);
    return event;
  }

  @Test
  void shouldTieEveryLineToItsTraceAndSpan() {
    // when
    var json = format(event(Level.INFO, Map.of("traceId", "4bf92f35", "spanId", "00f067aa")));

    // then
    assertThat(JsonPath.<String>read(json, "$['severity']")).isEqualTo("INFO");
    assertThat(JsonPath.<String>read(json, "$['logging.googleapis.com/trace']"))
        .isEqualTo("projects/demo-project/traces/4bf92f35");
    assertThat(JsonPath.<String>read(json, "$['logging.googleapis.com/spanId']"))
        .isEqualTo("00f067aa");
  }

  @Test
  void shouldSpeakGooglesSeverityForWarnings() {
    assertThat(JsonPath.<String>read(format(event(Level.WARN, Map.of())), "$['severity']"))
        .isEqualTo("WARNING");
  }

  @Test
  void shouldAddNothingWithoutAGoogleCloudProject() {
    // given : JSON logs on a laptop, no project
    var laptop = new CloudLoggingJsonMembersCustomizer(new MockEnvironment());
    JsonWriter<ILoggingEvent> writer =
        JsonWriter.of(
            members -> {
              members.add("message", ILoggingEvent::getFormattedMessage);
              laptop.customize(members);
            });

    // when
    var json = writer.writeToString(event(Level.INFO, Map.of("traceId", "4bf92f35")));

    // then
    assertThat(json).doesNotContain("severity", "logging.googleapis.com/", "projects/null");
  }

  @Test
  void shouldLeaveTheTraceOutOfLinesThatHaveNone() {
    // when : a startup line, logged outside of any request
    var json = format(event(Level.INFO, Map.of()));

    // then
    assertThat(json).doesNotContain("logging.googleapis.com/");
  }
}
