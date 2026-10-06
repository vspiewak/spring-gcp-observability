package com.vspiewak.orders.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("orders")
public record Order(@Id String orderId, Integer amount) {}
