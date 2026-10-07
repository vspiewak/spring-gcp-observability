package com.vspiewak.observability.sample;

import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Observed
public class SampleService {

  private static final Logger log = LoggerFactory.getLogger(SampleService.class);

  private final SampleRepository repository;

  private final RestClient downstream;

  public SampleService(
      SampleRepository repository,
      RestClient.Builder builder,
      @Value("${downstream.url}") String downstreamUrl) {
    this.repository = repository;
    this.downstream = builder.baseUrl(downstreamUrl).build();
  }

  public Optional<Sample> find(String id) {
    var sample = repository.findById(id);
    if (sample.isEmpty()) {
      log.warn("no sample {}", id);
      return sample;
    }
    downstream.get().uri("/downstream").retrieve().toBodilessEntity();
    log.info("found sample {}", id);
    return sample;
  }
}
