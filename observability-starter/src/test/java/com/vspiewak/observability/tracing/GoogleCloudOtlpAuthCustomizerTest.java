package com.vspiewak.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class GoogleCloudOtlpAuthCustomizerTest {

  private static final URI TELEMETRY_API = URI.create(CloudTraceAutoConfiguration.TELEMETRY_API);

  private static Credentials token(String value) {
    return GoogleCredentials.create(
        new AccessToken(value, Date.from(Instant.now().plusSeconds(3600))));
  }

  @Test
  void shouldSendABearerTokenToGoogle() {
    // given
    var customizer = new GoogleCloudOtlpAuthCustomizer(() -> token("t0k3n"), TELEMETRY_API);

    // when
    var headers = customizer.authorizationHeaders();

    // then
    assertThat(headers).containsEntry("Authorization", "Bearer t0k3n");
  }

  @Test
  void shouldResolveTheCredentialsOnceNotOnEveryExport() {
    // given
    var resolutions = new AtomicInteger();
    Supplier<Credentials> credentials =
        () -> {
          resolutions.incrementAndGet();
          return token("t0k3n");
        };
    var customizer = new GoogleCloudOtlpAuthCustomizer(credentials, TELEMETRY_API);

    // when
    customizer.authorizationHeaders();
    customizer.authorizationHeaders();

    // then
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void shouldExportWithoutHeadersRatherThanFailWhenThereAreNoCredentials() {
    // given
    var customizer =
        new GoogleCloudOtlpAuthCustomizer(
            () -> {
              throw new IllegalStateException("no ADC on this machine");
            },
            TELEMETRY_API);

    // when / then
    assertThat(customizer.authorizationHeaders()).isEmpty();
  }

  @Test
  void shouldAuthenticateTheExporterTowardsGoogle() {
    // given
    var builder = mock(OtlpHttpSpanExporterBuilder.class);

    // when
    new GoogleCloudOtlpAuthCustomizer(() -> token("t0k3n"), TELEMETRY_API).customize(builder);

    // then
    verify(builder).setHeaders(any());
  }

  @Test
  void shouldNeverSendAGoogleTokenAnywhereElse() {
    // given
    var builder = mock(OtlpHttpSpanExporterBuilder.class);
    var jaeger = URI.create("http://localhost:4318/v1/traces");

    // when
    new GoogleCloudOtlpAuthCustomizer(() -> token("t0k3n"), jaeger).customize(builder);

    // then
    verify(builder, never()).setHeaders(any());
    assertThat(GoogleCloudOtlpAuthCustomizer.isGoogleApi(URI.create("https://notgoogleapis.com")))
        .isFalse();
  }
}
