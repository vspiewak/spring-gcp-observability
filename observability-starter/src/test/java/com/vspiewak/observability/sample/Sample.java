package com.vspiewak.observability.sample;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("samples")
public record Sample(@Id String id, Integer amount) {}
