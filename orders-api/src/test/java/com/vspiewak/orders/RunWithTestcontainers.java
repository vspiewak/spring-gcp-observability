package com.vspiewak.orders;

import org.springframework.boot.SpringApplication;

/**
 * The local dev loop : {@code ./mvnw -pl orders-api spring-boot:test-run} boots the service against
 * a MongoDB container — no Google Cloud project, so nothing leaves the laptop.
 */
public class RunWithTestcontainers {

  public static void main(String[] args) {
    SpringApplication.from(OrdersApiApplication::main).with(Containers.class).run(args);
  }
}
