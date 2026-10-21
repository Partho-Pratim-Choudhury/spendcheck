package com.parthopratim.spendcheck;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parthopratim.spendcheck.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    @Test
    void missingOrShortJwtSecretFailsFast() {
        assertThatThrownBy(() -> new AppProperties.Jwt("", Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new AppProperties.Jwt("too-short", Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
    }
}
