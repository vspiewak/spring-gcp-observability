package com.vspiewak.observability.sample;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

/**
 * Another service, stood in by a local HTTP server on a random port : answers every call, and
 * remembers the {@code traceparent} it was called with.
 */
public final class DownstreamStub implements AutoCloseable {

  private final HttpServer server;

  private volatile String traceparent;

  private DownstreamStub(HttpServer server) {
    this.server = server;
  }

  public static DownstreamStub start() {
    try {
      var stub = new DownstreamStub(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
      stub.server.createContext(
          "/downstream",
          exchange -> {
            stub.traceparent = exchange.getRequestHeaders().getFirst("traceparent");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
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
