package com.vspiewak.pricing.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.vspiewak.pricing.services.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The controller slice, with the real pricing rule behind it. */
@WebMvcTest(QuoteController.class)
@Import(PricingService.class)
class QuoteControllerTest {

  @Autowired private MockMvcTester mvc;

  @Test
  void shouldQuoteAnAmountWithItsVat() {
    // when
    var response = mvc.get().uri("/prices/v1/quotes?amount=7");

    // then
    assertThat(response)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"amount\": 7, \"vat\": 1.40, \"total\": 8.40}");
  }
}
