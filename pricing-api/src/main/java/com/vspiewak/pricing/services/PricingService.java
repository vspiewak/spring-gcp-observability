package com.vspiewak.pricing.services;

import com.vspiewak.pricing.domain.Quote;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

  private static final Logger log = LoggerFactory.getLogger(PricingService.class);

  private static final BigDecimal VAT_RATE = new BigDecimal("0.20");

  private final ObservationRegistry observationRegistry;

  public PricingService(ObservationRegistry observationRegistry) {
    this.observationRegistry = observationRegistry;
  }

  // on the method, named : the span reads pricing.quote, not PricingService#quote
  @Observed(contextualName = "pricing.quote")
  public Quote quote(int amount) {
    var net = BigDecimal.valueOf(amount);
    // a span of our own, around just this block : it shows up under pricing.quote
    var vat =
        Observation.createNotStarted("pricing.vat", observationRegistry)
            .lowCardinalityKeyValue("vat.rate", VAT_RATE.toPlainString())
            .observe(() -> net.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP));
    var quote = new Quote(amount, vat, net.add(vat));
    log.info("quoted {} at {}", amount, quote.total());
    return quote;
  }
}
