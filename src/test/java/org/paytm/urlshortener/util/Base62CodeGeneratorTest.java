package org.paytm.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62CodeGeneratorTest {

    private final Base62CodeGenerator generator = new Base62CodeGenerator();

    @Test
    void encodesPositiveIdsWithGeneratedPrefix() {
        assertThat(generator.encode(1)).isEqualTo("u_1");
        assertThat(generator.encode(62)).isEqualTo("u_10");
        assertThat(generator.encode(3844)).isEqualTo("u_100");
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> generator.encode(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Short-code source id must be positive");
    }
}
