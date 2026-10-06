package com.vspiewak.pricing.controllers;

import com.vspiewak.pricing.domain.Quote;
import com.vspiewak.pricing.services.PricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prices/v1")
public class QuoteController {

  private final PricingService service;

  public QuoteController(PricingService service) {
    this.service = service;
  }

  @GetMapping("/quotes")
  public Quote quote(@RequestParam int amount) {
    return service.quote(amount);
  }
}
