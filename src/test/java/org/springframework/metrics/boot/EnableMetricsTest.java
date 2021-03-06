/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.boot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.metrics.instrument.web.WebMetricsTagProvider;
import org.springframework.metrics.instrument.web.WebfluxMetricsWebFilter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnableMetricsTest {
    @Autowired
    RestTemplate external;

    @Autowired
    TestRestTemplate loopback;

    @Autowired
    SimpleMeterRegistry registry;

    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void restTemplateIsInstrumented() {
        external.getForObject("http://www.google.com", String.class);

        assertThat(registry.findMeter(Timer.class, "http_client_requests"))
            .containsInstanceOf(Timer.class)
            .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @Test
    void requestMappingIsInstrumented() {
        loopback.getForObject("/api/people", Set.class);

        assertThat(registry.findMeter(Timer.class, "http_server_requests"))
                .containsInstanceOf(Timer.class)
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @SpringBootApplication
    @EnableMetrics
    @Import(PersonController.class)
    static class MetricsApp {
        public static void main(String[] args) {
            SpringApplication.run(MetricsApp.class, "--debug");
        }

        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        public WebfluxMetricsWebFilter webfluxMetrics(WebMetricsTagProvider provider) {
            return new WebfluxMetricsWebFilter(registry(), provider, "http_server_requests");
        }
    }

    @RestController
    static class PersonController {
        @Timed
        @GetMapping("/api/people")
        Set<String> personName() {
            return Collections.singleton("Jon");
        }
    }
}