package com.vspiewak.observability.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The logs a service inherits from the starter, end to end : once the Google Cloud project is known
 * — as on Cloud Run — every line of a request is one JSON object Google Cloud Logging reads
 * natively, tied to the request's trace. The service sets nothing else.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.gcp.project-id=demo-project",
      "management.tracing.export.otlp.enabled=false"
    })
@AutoConfigureRestTestClient
@Import(Containers.class)
@ExtendWith(OutputCaptureExtension.class)
class CloudLoggingIT {

  private static final DownstreamStub downstream = DownstreamStub.start();

  @DynamicPropertySource
  static void downstreamService(DynamicPropertyRegistry registry) {
    registry.add("downstream.url", downstream::url);
  }

  @AfterAll
  static void stopDownstreamService() {
    downstream.close();
  }

  @Autowired private RestTestClient client;

  @Autowired private SampleRepository repository;

  @Test
  void shouldWriteEveryRequestLineAsACloudLoggingEntryTiedToItsTrace(CapturedOutput output) {
    // given
    repository.save(new Sample("42", 7));

    // when
    client.get().uri("/samples/42").exchange().expectStatus().isOk();

    // then
    var line =
        output
            .getOut()
            .lines()
            .filter(l -> l.contains("found sample 42"))
            .findFirst()
            .orElseThrow();
    String traceId = JsonPath.read(line, "$.traceId");
    assertThat(JsonPath.<String>read(line, "$.severity")).isEqualTo("INFO");
    assertThat(JsonPath.<String>read(line, "$['logging.googleapis.com/trace']"))
        .isEqualTo("projects/demo-project/traces/" + traceId);
    assertThat(JsonPath.<String>read(line, "$['logging.googleapis.com/spanId']"))
        .isEqualTo(JsonPath.read(line, "$.spanId"));
  }

  @Test
  void shouldMapAWarningToCloudLoggingsSeverity(CapturedOutput output) {
    // when
    client.get().uri("/samples/999").exchange().expectStatus().isNotFound();

    // then
    var line = output.getOut().lines().filter(l -> l.contains("no sample 999")).findFirst();
    assertThat(line)
        .hasValueSatisfying(
            l -> assertThat(JsonPath.<String>read(l, "$.severity")).isEqualTo("WARNING"));
  }
}
