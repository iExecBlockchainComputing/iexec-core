package com.iexec.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class AppConfig {
    // empty class used to declare enableRetry that should be in a configuration class
}