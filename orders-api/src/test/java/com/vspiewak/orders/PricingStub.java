package com.vspiewak.orders;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A stand-in pricing-api on a random port : answers every quote, and remembers the {@code
 * traceparent} orders-api called it with.
 */
public final class PricingStub implements AutoCloseable {

  private static final byte[] QUOTE =
      "{\"amount\": 7, \"vat\": 1.40, \"total\": 8.40}".getBytes(StandardCharsets.UTF_8);

  private final HttpServer server;

  private volatile String traceparent;

  private PricingStub(HttpServer server) {
    this.server = server;
  }

  public static PricingStub start() {
    try {
      var stub = new PricingStub(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
      stub.server.createContext(
          "/prices/v1/quotes",
          exchange -> {
            stub.traceparent = exchange.getRequestHeaders().getFirst("traceparent");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, QUOTE.length);
            try (var body = exchange.getResponseBody()) {
              body.write(QUOTE);
            }
          });
      stub.server.start();
      return stub;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public String url() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public String traceparent() {
    return traceparent;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
