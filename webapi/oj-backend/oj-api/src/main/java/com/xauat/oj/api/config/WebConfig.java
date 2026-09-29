package com.xauat.oj.api.config;

import com.xauat.oj.api.web.RequestIdFilter;
import com.xauat.oj.api.web.CsrfProtectionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${oj.upload.avatar-dir:./data/avatars}") private String avatarDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(avatarDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/avatars/**").addResourceLocations(location);
    }
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(0);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<CsrfProtectionFilter> csrfProtectionFilter() {
        FilterRegistrationBean<CsrfProtectionFilter> registration = new FilterRegistrationBean<>(new CsrfProtectionFilter());
        registration.setOrder(1); registration.addUrlPatterns("/auth/*"); return registration;
    }
}
