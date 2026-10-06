package com.vspiewak.pricing.services;

import com.vspiewak.pricing.domain.Quote;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Observed
public class PricingService {

  private static final Logger log = LoggerFactory.getLogger(PricingService.class);

  private static final BigDecimal VAT_RATE = new BigDecimal("0.20");

  public Quote quote(int amount) {
    var net = BigDecimal.valueOf(amount);
    var vat = net.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    var quote = new Quote(amount, vat, net.add(vat));
    log.info("quoted {} at {}", amount, quote.total());
    return quote;
  }
}
