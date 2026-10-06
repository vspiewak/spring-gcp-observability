package com.vspiewak.pricing.domain;

import java.math.BigDecimal;

public record Quote(Integer amount, BigDecimal vat, BigDecimal total) {}
