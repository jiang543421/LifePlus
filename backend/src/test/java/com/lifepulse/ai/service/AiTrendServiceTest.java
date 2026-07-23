package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.service.DailyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiTrendService 单元测试（spec §v2.2 trend）。
 *
 * <p>每个 case 一个独立 commit，便于 review 拆解。
 */
@ExtendWith(MockitoExtension.class)
class AiTrendServiceTest {

    @Mock
    private DailyReportService dailyReportService;

    private AiTrendService service;

    @BeforeEach
    void setUp() {
        service = new AiTrendService(dailyReportService);
    }

    /**
     * Case 1：窗口越界（&lt; 1 或 &gt; MAX_HISTORY_DAYS=30）→ 1001 VALIDATION。
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31, 100, Integer.MIN_VALUE})
    void range_windowOutOfRange_throws1001(int windowDays) {
        assertThatThrownBy(() -> service.range(1L, windowDays))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }
}