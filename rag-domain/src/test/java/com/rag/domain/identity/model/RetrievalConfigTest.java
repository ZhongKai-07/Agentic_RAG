package com.rag.domain.identity.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetrievalConfigTest {

    @Test
    void defaultConstructor_setsExpectedDefaults() {
        RetrievalConfig config = new RetrievalConfig();
        assertThat(config.maxAgentRounds()).isEqualTo(3);
        assertThat(config.maxSubQueries()).isEqualTo(3);
        assertThat(config.enableFastPath()).isFalse();
        assertThat(config.minSufficientChunks()).isEqualTo(5);
        assertThat(config.rawScoreThreshold()).isEqualTo(0.02);
    }

    @Test
    void maxSubQueries_returnsDefaultWhenZero() {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", 0, false, 5, 0.02);
        assertThat(config.maxSubQueries(3)).isEqualTo(3);
    }

    @Test
    void maxSubQueries_returnsConfiguredValueWhenPositive() {
        RetrievalConfig config = new RetrievalConfig(3, "semantic_header", "", 2, false, 5, 0.02);
        assertThat(config.maxSubQueries(3)).isEqualTo(2);
    }
}
