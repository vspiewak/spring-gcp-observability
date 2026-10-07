package com.vspiewak.observability.sample;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An ordinary service, for the end-to-end proofs : a controller, an {@code @Observed} service, a
 * MongoDB repository and a call to another service — and nothing about observability but the
 * starter on the classpath.
 */
@SpringBootApplication
public class SampleApplication {}
