package com.java.trazabilidad.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// SI APLICAMOS LA ANOTACION @Configuration EL INTERCEPTOR SERA A NIVEL GLOBAL (OJO CON ESO)
public class FeignTraceConfig {

    public static final String TRACE_ID = "traceId";

    @Bean
    public RequestInterceptor requestInterceptor() {

        return new RequestInterceptor() {

            @Override
            public void apply(RequestTemplate template) {

                String traceId = MDC.get(TRACE_ID);

                if (traceId != null) {
                    template.header(TRACE_ID, traceId);
                }

            }

        };

    }


}