package com.vspiewak.orders.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.vspiewak.orders.Containers;
import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The logs the service inherits from {@code observability-starter}, end to end : with structured
 * logging on — as on Cloud Run — every line of a request is one JSON object Google Cloud Logging
 * reads natively, tied to the request's trace.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "logging.structured.format.console=logstash",
      "spring.cloud.gcp.project-id=demo-project",
      "management.tracing.export.otlp.enabled=false",
      // no Google credentials in the build : Spring Cloud GCP would go looking for some
      "spring.cloud.gcp.core.enabled=false"
    })
@AutoConfigureRestTestClient
@Import(Containers.class)
@ExtendWith(OutputCaptureExtension.class)
class CloudLoggingIT {

  @Autowired private RestTestClient client;

  @Autowired private OrderRepository repository;

  @Test
  void shouldWriteEveryRequestLineAsACloudLoggingEntryTiedToItsTrace(CapturedOutput output) {
    // given
    repository.save(new Order(null, "42", 7));

    // when
    client.get().uri("/orders/v1/orders/42").exchange().expectStatus().isOk();

    // then
    var line =
        output.getOut().lines().filter(l -> l.contains("found order 42")).findFirst().orElseThrow();
    String traceId = JsonPath.read(line, "$.traceId");
    assertThat(JsonPath.<String>read(line, "$.severity")).isEqualTo("INFO");
    assertThat(JsonPath.<String>read(line, "$['logging.googleapis.com/trace']"))
        .isEqualTo("projects/demo-project/traces/" + traceId);
    assertThat(JsonPath.<String>read(line, "$['logging.googleapis.com/spanId']"))
        .isEqualTo(JsonPath.read(line, "$.spanId"));
    assertThat(line).doesNotContain("\"level\"", "@timestamp");
  }

  @Test
  void shouldMapAWarningToCloudLoggingsSeverity(CapturedOutput output) {
    // when
    client.get().uri("/orders/v1/orders/999").exchange().expectStatus().isNotFound();

    // then
    var line = output.getOut().lines().filter(l -> l.contains("no order 999")).findFirst();
    assertThat(line).hasValueSatisfying(l -> assertThat(l).contains("\"severity\":\"WARNING\""));
  }
}
