package com.xauat.oj.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Worker 没有 spring-web，JacksonAutoConfiguration 不生效，显式提供 ObjectMapper。 */
@Configuration
public class WorkerConfig {
    @Bean
    public ObjectMapper objectMapper() { return new ObjectMapper(); }
}
