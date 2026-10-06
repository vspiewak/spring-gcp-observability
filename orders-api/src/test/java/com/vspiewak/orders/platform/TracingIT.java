package com.vspiewak.orders.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vspiewak.orders.Containers;
import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.repositories.OrderRepository;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The tracing the service inherits from {@code observability-starter}, end to end : one request is
 * one trace from the HTTP server span down to the MongoDB driver, a trace Cloud Run started is
 * continued even when Cloud Run chose not to sample it, and every span names its Google Cloud
 * project. Spans are captured in memory, exactly as the OTLP exporter would hand them to Google —
 * which is switched off here, so nothing leaves the build.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.gcp.project-id=demo-project",
      "management.tracing.export.otlp.enabled=false",
      // no Google credentials in the build : Spring Cloud GCP would go looking for some
      "spring.cloud.gcp.core.enabled=false"
    })
@AutoConfigureRestTestClient
@AutoConfigureTracing
@Import({Containers.class, TracingIT.CapturedSpans.class})
class TracingIT {

  /** What Cloud Run's front end hands the container : a trace it started, and did not sample. */
  private static final String CLOUD_RUN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private static final String CLOUD_RUN_SPAN_ID = "00f067aa0ba902b7";

  @Autowired private RestTestClient client;

  @Autowired private OrderRepository repository;

  @Autowired private InMemorySpanExporter spans;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    repository.save(new Order(null, "42", 7));
    spans.reset();
  }

  @Test
  void shouldTraceARequestFromHttpDownToMongo() {
    // when
    client.get().uri("/orders/v1/orders/42").exchange().expectStatus().isOk();

    // then
    var server = awaitServerSpan();
    var service = onlyChild(server, "OrderService#findByOrderId");
    assertThat(childrenOf(service))
        .isNotEmpty()
        .allSatisfy(
            span -> {
              assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
              assertThat(span.getName()).startsWith("find ");
            });
  }

  @Test
  void shouldContinueTheTraceCloudRunStartedEvenWhenCloudRunDidNotSampleIt() {
    // when : the "-00" flag is Cloud Run declining to sample this request
    client
        .get()
        .uri("/orders/v1/orders/42")
        .header("traceparent", "00-%s-%s-00".formatted(CLOUD_RUN_TRACE_ID, CLOUD_RUN_SPAN_ID))
        .exchange()
        .expectStatus()
        .isOk();

    // then : recorded anyway, and still part of Cloud Run's trace
    var server = awaitServerSpan();
    assertThat(server.getTraceId()).isEqualTo(CLOUD_RUN_TRACE_ID);
    assertThat(server.getParentSpanId()).isEqualTo(CLOUD_RUN_SPAN_ID);
  }

  @Test
  void shouldNameTheGoogleCloudProjectOnEverySpan() {
    // when
    client.get().uri("/orders/v1/orders/42").exchange().expectStatus().isOk();

    // then
    awaitServerSpan();
    assertThat(spans.getFinishedSpanItems())
        .allSatisfy(
            span ->
                assertThat(
                        span.getResource().getAttribute(AttributeKey.stringKey("gcp.project_id")))
                    .isEqualTo("demo-project"));
  }

  private SpanData awaitServerSpan() {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                spans.getFinishedSpanItems().stream()
                    .filter(span -> span.getKind() == SpanKind.SERVER)
                    .filter(span -> span.getName().endsWith(" /orders/v1/orders/{orderId}"))
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private SpanData onlyChild(SpanData parent, String name) {
    var children = childrenOf(parent);
    assertThat(children)
        .as("children of %s, among %s", parent, spans.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactly(name);
    return children.getFirst();
  }

  private List<SpanData> childrenOf(SpanData parent) {
    return spans.getFinishedSpanItems().stream()
        .filter(span -> span.getParentSpanId().equals(parent.getSpanId()))
        .toList();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CapturedSpans {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
      return InMemorySpanExporter.create();
    }

    @Bean
    SpanProcessor inMemorySpanProcessor(InMemorySpanExporter exporter) {
      return SimpleSpanProcessor.create(exporter);
    }
  }
}
