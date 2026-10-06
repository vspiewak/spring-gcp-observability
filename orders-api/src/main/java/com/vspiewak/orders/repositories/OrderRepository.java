package com.vspiewak.orders.repositories;

import com.vspiewak.orders.domain.Order;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<Order, String> {

  Optional<Order> findByOrderId(String orderId);
}
