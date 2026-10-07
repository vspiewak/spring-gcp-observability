package com.vspiewak.orders.domain;

import java.math.BigDecimal;

public record PricedOrder(String orderId, int amount, BigDecimal total) {}
