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

  private static String format(MockEnvironment environment, ILoggingEvent event) {
    var customizer = new CloudLoggingJsonMembersCustomizer(environment);
    // the members Boot's logstash format writes, minus the ones this test has no use for
    JsonWriter<ILoggingEvent> writer =
        JsonWriter.of(
            members -> {
              members.add("@timestamp", "2026-10-05T22:00:00.000+02:00");
              members.add("@version", "1");
              members.add("message", ILoggingEvent::getFormattedMessage);
              members.add("level", ILoggingEvent::getLevel);
              members.add("level_value", ILoggingEvent::getLevel).as(Level::toInt);
              members
                  .add()
                  .usingPairs((event1, pairs) -> event1.getMDCPropertyMap().forEach(pairs));
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

  private static MockEnvironment project(String projectId) {
    return new MockEnvironment().withProperty("spring.cloud.gcp.project-id", projectId);
  }

  @Test
  void shouldTieEveryLineToItsTraceTheWayCloudLoggingExpects() {
    // given
    var event = event(Level.INFO, Map.of("traceId", "4bf92f35", "spanId", "00f067aa"));

    // when
    var json = format(project("demo-project"), event);

    // then
    assertThat(JsonPath.<String>read(json, "$['severity']")).isEqualTo("INFO");
    assertThat(JsonPath.<String>read(json, "$['logging.googleapis.com/trace']"))
        .isEqualTo("projects/demo-project/traces/4bf92f35");
    assertThat(JsonPath.<String>read(json, "$['logging.googleapis.com/spanId']"))
        .isEqualTo("00f067aa");
    assertThat(JsonPath.<String>read(json, "$['message']")).isEqualTo("found order 42");
  }

  @Test
  void shouldSpeakGooglesNamesNotLogstashs() {
    // when
    var json = format(project("demo-project"), event(Level.INFO, Map.of()));

    // then
    assertThat(JsonPath.<String>read(json, "$['time']")).isEqualTo("2026-10-05T22:00:00.000+02:00");
    assertThat(json).doesNotContain("@timestamp", "@version", "\"level\"", "level_value");
  }

  @Test
  void shouldLeaveTheTraceOutOfLinesThatHaveNone() {
    // when : a startup line, logged outside of any request
    var json = format(project("demo-project"), event(Level.INFO, Map.of()));

    // then
    assertThat(json)
        .doesNotContain("logging.googleapis.com/trace", "logging.googleapis.com/spanId");
  }

  @Test
  void shouldMapLevelsToCloudLoggingSeverities() {
    assertThat(CloudLoggingJsonMembersCustomizer.severity(Level.ERROR)).isEqualTo("ERROR");
    assertThat(CloudLoggingJsonMembersCustomizer.severity(Level.WARN)).isEqualTo("WARNING");
    assertThat(CloudLoggingJsonMembersCustomizer.severity(Level.INFO)).isEqualTo("INFO");
    assertThat(CloudLoggingJsonMembersCustomizer.severity(Level.DEBUG)).isEqualTo("DEBUG");
    assertThat(CloudLoggingJsonMembersCustomizer.severity(Level.TRACE)).isEqualTo("DEBUG");
  }

  @Test
  void shouldChangeNothingWithoutAGoogleCloudProject() {
    // when
    var json =
        format(
            new MockEnvironment(), event(Level.WARN, Map.of("traceId", "4bf92f35", "spanId", "1")));

    // then
    assertThat(JsonPath.<String>read(json, "$['level']")).isEqualTo("WARN");
    assertThat(json).doesNotContain("severity", "logging.googleapis.com");
  }
}
