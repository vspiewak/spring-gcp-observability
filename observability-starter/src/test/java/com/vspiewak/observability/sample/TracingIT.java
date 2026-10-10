package com.vspiewak.observability.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The tracing a service inherits from the starter, end to end : one request is one trace from the
 * HTTP server span down to the MongoDB driver and out to the next service, and a trace Cloud Run
 * started is continued even when Cloud Run chose not to sample it. Spans are captured in memory,
 * exactly as the OTLP exporter would hand them to the collector — which is switched off here, so
 * nothing leaves the build.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.gcp.project-id=demo-project",
      "management.tracing.export.otlp.enabled=false"
    })
@AutoConfigureRestTestClient
@AutoConfigureTracing
@Import({Containers.class, TracingIT.CapturedSpans.class})
class TracingIT {

  /** What Cloud Run's front end hands the container : a trace it started, and did not sample. */
  private static final String CLOUD_RUN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private static final String CLOUD_RUN_SPAN_ID = "00f067aa0ba902b7";

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

  /**
   * Not a bean : Boot hands every {@code SpanExporter} bean to its own batch processor, which would
   * deliver each span a second time, seconds later — possibly into the next test.
   */
  private static final InMemorySpanExporter spans = InMemorySpanExporter.create();

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    repository.save(new Sample("42", 7));
    spans.reset();
  }

  @Test
  void shouldTraceARequestFromHttpDownToMongoAndOut() {
    // when
    client.get().uri("/samples/42").exchange().expectStatus().isOk();

    // then
    var server = awaitServerSpan();
    var service = onlyChild(server, "SampleService#find");
    assertThat(childrenOf(service))
        .allSatisfy(span -> assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT))
        .extracting(SpanData::getName)
        .contains("http get")
        .anyMatch(name -> name.startsWith("find "));
  }

  @Test
  void shouldCarryTheTraceToTheNextService() {
    // when
    client.get().uri("/samples/42").exchange().expectStatus().isOk();

    // then : the next service was called inside this trace, as a child of the outgoing call's span
    var server = awaitServerSpan();
    var call =
        childrenOf(onlyChild(server, "SampleService#find")).stream()
            .filter(span -> span.getName().equals("http get"))
            .findFirst()
            .orElseThrow();
    // traceparent : version - trace id - parent span id - flags (0x01 sampled, 0x02 random id)
    var traceparent = downstream.traceparent().split("-");
    assertThat(traceparent[1]).isEqualTo(server.getTraceId());
    assertThat(traceparent[2]).isEqualTo(call.getSpanId());
    assertThat(Integer.parseInt(traceparent[3], 16) & 0x01).as("sampled").isEqualTo(1);
  }

  @Test
  void shouldContinueTheTraceCloudRunStartedEvenWhenCloudRunDidNotSampleIt() {
    // when : the "-00" flag is Cloud Run declining to sample this request
    client
        .get()
        .uri("/samples/42")
        .header("traceparent", "00-%s-%s-00".formatted(CLOUD_RUN_TRACE_ID, CLOUD_RUN_SPAN_ID))
        .exchange()
        .expectStatus()
        .isOk();

    // then : recorded anyway, and still part of Cloud Run's trace
    var server = awaitServerSpan();
    assertThat(server.getTraceId()).isEqualTo(CLOUD_RUN_TRACE_ID);
    assertThat(server.getParentSpanId()).isEqualTo(CLOUD_RUN_SPAN_ID);
  }

  private SpanData awaitServerSpan() {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                spans.getFinishedSpanItems().stream()
                    .filter(span -> span.getKind() == SpanKind.SERVER)
                    .filter(span -> span.getName().endsWith(" /samples/{id}"))
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
    SpanProcessor inMemorySpanProcessor() {
      return SimpleSpanProcessor.create(spans);
    }
  }
}
