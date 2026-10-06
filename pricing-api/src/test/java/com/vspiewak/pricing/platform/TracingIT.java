package com.vspiewak.pricing.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.Optional;
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
 * pricing-api is called by orders-api, never by a browser : its traces must continue the caller's.
 * The request below carries the {@code traceparent} orders-api sends — the spans land in that
 * trace.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "management.tracing.export.otlp.enabled=false",
      // no Google credentials in the build : Spring Cloud GCP would go looking for some
      "spring.cloud.gcp.core.enabled=false"
    })
@AutoConfigureRestTestClient
@AutoConfigureTracing
@Import(TracingIT.CapturedSpans.class)
class TracingIT {

  private static final String CALLER_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private static final String CALLER_SPAN_ID = "00f067aa0ba902b7";

  @Autowired private RestTestClient client;

  @Autowired private InMemorySpanExporter spans;

  @Test
  void shouldContinueTheCallersTrace() {
    // when
    client
        .get()
        .uri("/prices/v1/quotes?amount=7")
        .header("traceparent", "00-%s-%s-01".formatted(CALLER_TRACE_ID, CALLER_SPAN_ID))
        .exchange()
        .expectStatus()
        .isOk();

    // then
    var server = awaitServerSpan();
    assertThat(server.getTraceId()).isEqualTo(CALLER_TRACE_ID);
    assertThat(server.getParentSpanId()).isEqualTo(CALLER_SPAN_ID);
    assertThat(spans.getFinishedSpanItems())
        .filteredOn(span -> span.getParentSpanId().equals(server.getSpanId()))
        .extracting(SpanData::getName)
        .containsExactly("PricingService#quote");
  }

  private SpanData awaitServerSpan() {
    return await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                spans.getFinishedSpanItems().stream()
                    .filter(span -> span.getKind() == SpanKind.SERVER)
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
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
