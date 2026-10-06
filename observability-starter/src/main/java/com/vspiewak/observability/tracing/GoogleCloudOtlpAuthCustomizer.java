package com.vspiewak.observability.tracing;

import com.google.auth.Credentials;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpHttpSpanExporterBuilderCustomizer;
import org.springframework.util.function.SingletonSupplier;

/**
 * Authenticates the OTLP export against Google's Telemetry API — by hand, as Google's auth
 * extension only hooks into the Java agent or the SDK's own autoconfiguration. The headers are read
 * again on every export, so the token refreshes itself ; a Google token only ever goes to a Google
 * host.
 */
public class GoogleCloudOtlpAuthCustomizer implements OtlpHttpSpanExporterBuilderCustomizer {

  private static final Log logger = LogFactory.getLog(GoogleCloudOtlpAuthCustomizer.class);

  private final Supplier<Credentials> credentials;

  private final URI endpoint;

  public GoogleCloudOtlpAuthCustomizer(Supplier<Credentials> credentials, URI endpoint) {
    this.credentials = SingletonSupplier.of(credentials);
    this.endpoint = endpoint;
  }

  @Override
  public void customize(OtlpHttpSpanExporterBuilder builder) {
    if (isGoogleApi(this.endpoint)) {
      builder.setHeaders(this::authorizationHeaders);
    }
  }

  Map<String, String> authorizationHeaders() {
    try {
      Map<String, String> headers = new LinkedHashMap<>();
      this.credentials
          .get()
          .getRequestMetadata(this.endpoint)
          .forEach((name, values) -> headers.put(name, String.join(",", values)));
      return headers;
    } catch (Exception ex) {
      logger.warn("No Google credentials for the trace export, spans will be rejected", ex);
      return Map.of();
    }
  }

  static boolean isGoogleApi(URI endpoint) {
    String host = endpoint.getHost();
    return host != null && (host.equals("googleapis.com") || host.endsWith(".googleapis.com"));
  }
}
