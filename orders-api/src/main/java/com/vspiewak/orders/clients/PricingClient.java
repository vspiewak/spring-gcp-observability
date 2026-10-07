package com.vspiewak.orders.clients;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PricingClient {

  public record Quote(int amount, BigDecimal vat, BigDecimal total) {}

  private final RestClient restClient;

  public PricingClient(RestClient.Builder builder, @Value("${pricing.url}") String pricingUrl) {
    this.restClient = builder.baseUrl(pricingUrl).build();
  }

  public Quote quote(int amount) {
    return restClient
        .get()
        .uri("/prices/v1/quotes?amount={amount}", amount)
        .retrieve()
        .body(Quote.class);
  }
}
