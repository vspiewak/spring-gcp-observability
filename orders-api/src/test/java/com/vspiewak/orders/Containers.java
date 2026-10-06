package com.vspiewak.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/** One MongoDB container per Spring context — contexts never share (and re-seed) a database. */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

  @Bean
  @ServiceConnection
  MongoDBContainer mongoContainer() {
    return new MongoDBContainer("mongo:8.0");
  }
}
