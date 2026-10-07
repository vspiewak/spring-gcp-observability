package com.vspiewak.observability.sample;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SampleController {

  private final SampleService service;

  public SampleController(SampleService service) {
    this.service = service;
  }

  @GetMapping("/samples/{id}")
  public Sample sample(@PathVariable String id) {
    return service.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
