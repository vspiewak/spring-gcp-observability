package com.vspiewak.orders.services;

import com.vspiewak.orders.clients.PricingClient;
import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.domain.PricedOrder;
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

  private final PricingClient pricing;

  public OrderService(OrderRepository repository, PricingClient pricing) {
    this.repository = repository;
    this.pricing = pricing;
  }

  public Order create(String orderId, Integer amount) {
    var order = repository.save(new Order(orderId, amount));
    log.info("created order {}", orderId);
    return order;
  }

  public Optional<PricedOrder> findByOrderId(String orderId) {
    var order = repository.findById(orderId);
    if (order.isEmpty()) {
      log.warn("no order {}", orderId);
      return Optional.empty();
    }
    var quote = pricing.quote(order.get().amount());
    log.info("found order {}, priced at {}", orderId, quote.total());
    return Optional.of(new PricedOrder(orderId, order.get().amount(), quote.total()));
  }
}
