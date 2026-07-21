package com.lifepulse.ai.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifepulse.ai.model.Trend;
import org.junit.jupiter.api.Test;

class AiChipDtoTest {

    @Test
    void empty_factory_usesPlaceholderValues() {
        var chip = AiChipDto.empty("taskCompletion", "任务完成率");

        assertThat(chip.key()).isEqualTo("taskCompletion");
        assertThat(chip.label()).isEqualTo("任务完成率");
        assertThat(chip.value()).isEqualTo("—");
        assertThat(chip.unit()).isEqualTo("");
        assertThat(chip.trend()).isEqualTo(Trend.NONE);
        assertThat(chip.deltaText()).isEqualTo("");
    }
}