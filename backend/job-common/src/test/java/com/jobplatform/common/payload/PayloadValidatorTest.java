package com.jobplatform.common.payload;

import com.jobplatform.common.domain.JobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadValidatorTest {

    @Test
    void textProcessingRequiresNonBlankText() {
        assertThatCode(() -> PayloadValidator.validate(JobType.TEXT_PROCESSING, "hello world")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.TEXT_PROCESSING, "   "))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void reportGenerationRequiresHeaderAndConsistentRows() {
        assertThatCode(() -> PayloadValidator.validate(JobType.REPORT_GENERATION, "a,b\n1,2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.REPORT_GENERATION, "a,b"))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("at least one data row");
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.REPORT_GENERATION, "a,b\n1,2,3"))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("row 1");
    }

    @Test
    void dataProcessingRequiresOnlyNumbers() {
        assertThatCode(() -> PayloadValidator.validate(JobType.DATA_PROCESSING, "1, 2.5\n3")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.DATA_PROCESSING, "1, 2, x"))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("'x'");
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.DATA_PROCESSING, ""))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void failureSimulationValidatesItsSettings() {
        assertThatCode(() -> PayloadValidator.validate(JobType.FAILURE_SIMULATION, "failures=2\ndelay_ms=100"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.FAILURE_SIMULATION, "failures=abc"))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> PayloadValidator.validate(JobType.FAILURE_SIMULATION, "failures=11"))
                .isInstanceOf(InvalidPayloadException.class);
    }
}
