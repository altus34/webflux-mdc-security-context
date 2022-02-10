package org.example.springboot.mdc;

import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SessionWebConfig implements WebFilter {
    private static final String MDC_CONTEXT_REACTOR_KEY = SessionWebConfig.class.getName();
    public static final String SESSION_ID = "x-session-id";

    @PostConstruct
    private void contextOperatorHook() {
        Hooks.onEachOperator(MDC_CONTEXT_REACTOR_KEY,
                Operators.lift((scannable, coreSubscriber) -> new MdcContextLifter<>(coreSubscriber)));
    }

    @PreDestroy
    private void cleanupHook() {
        Hooks.resetOnEachOperator(MDC_CONTEXT_REACTOR_KEY);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String sessionId = getSessionId(request.getHeaders());
        return chain.filter(exchange).contextWrite(Context.of(SESSION_ID, sessionId));
    }

    private String getSessionId(HttpHeaders headers) {
        List<String> sessionHeaders = headers.get(SESSION_ID);
        if (sessionHeaders != null && !sessionHeaders.isEmpty()) {
            return sessionHeaders.get(0);
        }
        return "";
    }

    public static class MdcContextLifter<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<T>  coreSubscriber;

        public MdcContextLifter(CoreSubscriber<T> coreSubscriber) {
            this.coreSubscriber = coreSubscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            coreSubscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(T obj) {
            copyToMdc(coreSubscriber.currentContext());
            coreSubscriber.onNext(obj);
        }

        @Override
        public void onError(Throwable throwable) {
            coreSubscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            coreSubscriber.onComplete();
        }

        @Override
        public Context currentContext() {
            return coreSubscriber.currentContext();
        }

        private void copyToMdc(Context context) {
            if(!context.isEmpty()) {
                Map<String, String> map = context.stream()
                        .filter(e -> e.getKey().toString().equals(SESSION_ID))
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));

                MDC.setContextMap(map);
            } else {
                MDC.clear();
            }
        }
    }
}
