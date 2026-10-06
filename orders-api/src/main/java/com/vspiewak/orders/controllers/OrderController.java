package com.vspiewak.orders.controllers;

import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.services.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders/v1/orders")
public class OrderController {

  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  public record NewOrder(String orderId, Integer amount) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Order createOrder(@RequestBody NewOrder order) {
    return service.create(order.orderId(), order.amount());
  }

  @GetMapping("/{orderId}")
  public Order getOrder(@PathVariable String orderId) {
    return service
        .findByOrderId(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
