package com.lifepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用入口。
 *
 * <p>{@link ConfigurationPropertiesScan} 扫描 {@code com.lifepulse.**} 包下所有
 * {@code @ConfigurationProperties} bean（如 {@code com.lifepulse.auth.config.JwtProperties}）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.lifepulse")
public class LifePulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifePulseApplication.class, args);
    }
}
