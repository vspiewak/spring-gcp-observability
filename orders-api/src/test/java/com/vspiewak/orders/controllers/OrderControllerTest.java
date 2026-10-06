package com.vspiewak.orders.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.vspiewak.orders.domain.Order;
import com.vspiewak.orders.services.OrderService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The controller slice : no server, no MongoDB — the service is mocked. */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private OrderService service;

  @Test
  void shouldCreateAnOrder() {
    // given
    given(service.create("42", 7)).willReturn(new Order("id-1", "42", 7));

    // when
    var response =
        mvc.post()
            .uri("/orders/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\": \"42\", \"amount\": 7}");

    // then
    assertThat(response)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.orderId")
        .isEqualTo("42");
  }

  @Test
  void shouldReturnOneOrderAsJson() {
    // given
    given(service.findByOrderId("42")).willReturn(Optional.of(new Order("id-1", "42", 7)));

    // when
    var response = mvc.get().uri("/orders/v1/orders/42");

    // then
    assertThat(response).hasStatusOk().bodyJson().extractingPath("$.amount").isEqualTo(7);
  }

  @Test
  void shouldReturnNotFoundForAnUnknownOrder() {
    // given
    given(service.findByOrderId("999")).willReturn(Optional.empty());

    // when
    var response = mvc.get().uri("/orders/v1/orders/999");

    // then
    assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
  }
}
