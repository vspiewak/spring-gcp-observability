package com.vspiewak.orders.services;

import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.repositories.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Observed
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository repository;

  public OrderService(OrderRepository repository) {
    this.repository = repository;
  }

  public Order create(String orderId, Integer amount) {
    var order = repository.save(new Order(null, orderId, amount));
    log.info("created order {}", orderId);
    return order;
  }

  public Optional<Order> findByOrderId(String orderId) {
    var order = repository.findByOrderId(orderId);
    if (order.isPresent()) {
      log.info("found order {}", orderId);
    } else {
      log.warn("no order {}", orderId);
    }
    return order;
  }
}
