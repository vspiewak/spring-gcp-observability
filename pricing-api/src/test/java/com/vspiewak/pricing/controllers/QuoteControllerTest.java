package com.vspiewak.pricing.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.vspiewak.pricing.domain.Quote;
import com.vspiewak.pricing.services.PricingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(QuoteController.class)
class QuoteControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PricingService service;

  @Test
  void shouldQuoteAnAmountWithItsVat() {
    // given
    given(service.quote(7))
        .willReturn(new Quote(7, new BigDecimal("1.40"), new BigDecimal("8.40")));

    // when
    var response = mvc.get().uri("/prices/v1/quotes?amount=7");

    // then
    assertThat(response)
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"amount": 7, "vat": 1.40, "total": 8.40}
            """);
  }
}
