package org.example.springboot.mdc;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import static org.example.springboot.mdc.SessionWebConfig.SESSION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(OutputCaptureExtension.class)
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT,
        classes = {
                MDCSessionIdTest.LogController.class,
                SessionWebConfig.class
        }
)
@EnableAutoConfiguration
public class MDCSessionIdTest {
    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Should add Session Id in MDC context in log")
    void sessionIdMDCContext(CapturedOutput output) {
        webTestClient
                .get()
                .uri("/test/log")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> response, equalTo("Ok"));

        String log = output.getOut();
        assertThat(log, containsString("\"message\":\"Log message 1\""));
        assertThat(log, containsString("\"message\":\"Log message 2\""));
        assertThat(log, containsString("\"message\":\"Log message 3\""));

        webTestClient
                .get()
                .uri("/test/log")
                .header(SESSION_ID, "my_session_id")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(response -> response, equalTo("Ok"));

        log = output.getOut();
        assertThat(log, containsString("\"x-session-id\":\"my_session_id\""));
    }

    @RestController
    @RequestMapping(value = "/test")
    public static class LogController {
        private static final Logger LOGGER = LoggerFactory.getLogger(LogController.class);

        @GetMapping("/log")
        public Mono<String> log() {
            LOGGER.info("Log message 1");
            return Mono.just("Ok")
                    .doOnNext(s -> LOGGER.info("Log message 2"))
                    .map(s -> {
                        LOGGER.info("Log message 3");
                        return s;
                    });
        }
    }
}
