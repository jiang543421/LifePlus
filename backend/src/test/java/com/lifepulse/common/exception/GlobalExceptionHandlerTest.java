package com.lifepulse.common.exception;

import com.lifepulse.auth.AuthConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1.3-A · {@link GlobalExceptionHandler} 切片测试（plan §8）。
 *
 * <p>目标断言（spec §03 §3）：
 * <ul>
 *   <li>{@link BusinessException} → 按 code 映射 HTTP 状态，信封 {@code code} 透传</li>
 *   <li>{@link org.springframework.web.bind.MethodArgumentNotValidException} → 400 + code 1001</li>
 *   <li>{@link Exception} 兜底 → 500 + code 1500</li>
 * </ul>
 *
 * <p>使用 {@code MockMvcBuilders.standaloneSetup(...)} 直接构造 MockMvc，绕开
 * Spring bean 扫描对 nested 静态 controller 的不可见问题。Security 由
 * {@code addFilters=false} 隐式避开（standalone 无 filter chain）。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders
                .standaloneSetup(new TestStubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void businessException_mapsToMappedHttpStatusAndCarriesCode() throws Exception {
        mvc.perform(get("/__test__/business"))
                .andExpect(status().isUnauthorized())              // 1002 → 401
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS))
                .andExpect(jsonPath("$.message").value("test invalid creds"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void businessException_conflictMapsTo409() throws Exception {
        mvc.perform(get("/__test__/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_EMAIL_TAKEN));
    }

    @Test
    void validationException_mapsTo400WithCode1001() throws Exception {
        mvc.perform(post("/__test__/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));
    }

    @Test
    void genericException_mapsTo500WithCode1500() throws Exception {
        mvc.perform(get("/__test__/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1500));
    }

    @RestController
    @RequestMapping("/__test__")
    static class TestStubController {
        @GetMapping("/business")
        public void business() {
            throw new BusinessException(AuthConstants.ERR_BAD_CREDENTIALS, "test invalid creds");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new BusinessException(AuthConstants.ERR_EMAIL_TAKEN, "email taken");
        }

        @PostMapping("/validated")
        public void validated(@Valid @RequestBody ValidatedReq req) {
            // force @Valid path; never normally reached
        }

        @GetMapping("/generic")
        public void generic() {
            throw new RuntimeException("boom");
        }
    }

    record ValidatedReq(@NotBlank String email) {}
}
