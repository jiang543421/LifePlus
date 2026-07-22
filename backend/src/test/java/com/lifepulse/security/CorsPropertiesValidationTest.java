package com.lifepulse.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CorsProperties fail-fast 单测（issue R-004 AC-2）。
 *
 * <p>不依赖 Spring 上下文；直接 {@code new CorsProperties()} → {@code setAllowedOrigins(...)} →
 * 手动调 {@code validate()}（{@code @PostConstruct} 在 new 阶段不会触发）。
 */
class CorsPropertiesValidationTest {

    @Test
    void validate_emptyList_throws() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(List.of());
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void validate_wildcard_throws() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(List.of("*"));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must NOT contain wildcard");
    }

    @Test
    void validate_blankEntry_throws() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(java.util.Arrays.asList("http://valid.test", "  "));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void validate_nonHttpScheme_throws() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(List.of("ftp://wrong.scheme"));
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void validate_validList_doesNotThrow() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(List.of("http://localhost", "https://app.lifepulse.com"));
        // 不抛即通过
        props.validate();
    }
}
