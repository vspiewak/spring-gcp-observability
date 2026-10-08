package com.vspiewak.pricing.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

  @InjectMocks private PricingService service;

  @Test
  void shouldAddTwentyPercentVat() {
    // when
    var quote = service.quote(7);

    // then
    assertThat(quote.vat()).isEqualByComparingTo(new BigDecimal("1.40"));
    assertThat(quote.total()).isEqualByComparingTo(new BigDecimal("8.40"));
  }
}
