package com.xauat.oj.api.web;

import com.xauat.oj.common.constant.RequestIdConstants;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final HealthEndpoint healthEndpoint;

    public HealthController(JdbcTemplate jdbcTemplate, HealthEndpoint healthEndpoint) {
        this.jdbcTemplate = jdbcTemplate;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping({"/", "/healthz"})
    public Map<String, Object> health(HttpServletRequest request) {
        return body("ok", request);
    }

    @GetMapping("/readyz")
    public Map<String, Object> ready(HttpServletRequest request) {
        jdbcTemplate.queryForObject("select 1", Integer.class);
        return body("ready", request);
    }

    @GetMapping("/healthz/db")
    public Map<String, Object> database(HttpServletRequest request) {
        jdbcTemplate.queryForObject("select 1", Integer.class);
        return body("ok", request);
    }

    private Map<String, Object> body(String status, HttpServletRequest request) {
        return Map.of("status", status, "request_id", request.getAttribute(RequestIdConstants.ATTRIBUTE));
    }
}
