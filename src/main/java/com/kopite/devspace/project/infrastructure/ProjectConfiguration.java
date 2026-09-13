package com.kopite.devspace.project.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProjectConfiguration {
    @Bean
    Clock projectClock() { return Clock.systemUTC(); }
}
