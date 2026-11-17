package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.payload.InvalidPayloadException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlerTests {

    private static JobContext context(JobType type, String payload, int attempt) {
        return new JobContext(1L, type, payload, attempt);
    }

    @Test
    void textProcessingCountsWordsSentencesAndFrequencies() {
        JobResult result = new TextProcessingHandler()
                .execute(context(JobType.TEXT_PROCESSING, "The cat sat. The cat ran! Dogs bark?", 1));

        assertThat(result.summary()).contains("words=8", "sentences=3", "unique_words=6", "longest_word=bark",
                "top_words=cat:2,the:2,bark:1,dogs:1,ran:1");
    }

    @Test
    void textProcessingIsDeterministic() {
        TextProcessingHandler handler = new TextProcessingHandler();
        String text = "b a b a c";

        assertThat(handler.execute(context(JobType.TEXT_PROCESSING, text, 1)))
                .isEqualTo(handler.execute(context(JobType.TEXT_PROCESSING, text, 2)));
    }

    @Test
    void dataProcessingComputesStatistics() {
        JobResult result = new DataProcessingHandler().execute(context(JobType.DATA_PROCESSING, "2, 4, 4, 4, 5, 5, 7, 9", 1));

        assertThat(result.summary()).contains("count=8", "sum=40.0000", "min=2.0000", "max=9.0000", "mean=5.0000",
                "median=4.5000", "std_dev=2.0000");
    }

    @Test
    void dataProcessingRejectsNonNumericInputAsNonRetryable() {
        assertThatThrownBy(() -> new DataProcessingHandler().execute(context(JobType.DATA_PROCESSING, "1, abc", 1)))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void reportGenerationSummarisesNumericAndTextColumns() {
        String csv = "region,units,price\nnorth,10,2.50\nsouth,20,3.50\nnorth,30,4.00";

        JobResult result = new ReportGenerationHandler().execute(context(JobType.REPORT_GENERATION, csv, 1));

        assertThat(result.summary()).contains("rows=3", "columns=3",
                "column=region type=text distinct=2 most_common=north(2)",
                "column=units type=numeric sum=60 min=10 max=30 avg=20.00",
                "column=price type=numeric sum=10.00 min=2.50 max=4.00 avg=3.33");
    }

    @Test
    void reportGenerationRejectsRaggedRows() {
        assertThatThrownBy(() -> new ReportGenerationHandler()
                .execute(context(JobType.REPORT_GENERATION, "a,b\n1", 1)))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void failureSimulationFailsUntilTheConfiguredAttemptThenSucceeds() throws Exception {
        FailureSimulationHandler handler = new FailureSimulationHandler();
        String payload = "failures=2";

        assertThatThrownBy(() -> handler.execute(context(JobType.FAILURE_SIMULATION, payload, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("attempt 1");
        assertThatThrownBy(() -> handler.execute(context(JobType.FAILURE_SIMULATION, payload, 2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(handler.execute(context(JobType.FAILURE_SIMULATION, payload, 3)).summary())
                .contains("succeeded on attempt 3");
    }

    @Test
    void registryFindsEveryJobTypeAndRejectsMissingHandlers() {
        List<JobHandler> all = List.of(new TextProcessingHandler(), new DataProcessingHandler(),
                new ReportGenerationHandler(), new FailureSimulationHandler());
        JobHandlerRegistry registry = new JobHandlerRegistry(all);

        for (JobType type : JobType.values()) {
            assertThat(registry.find(type)).isPresent();
        }
        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(new TextProcessingHandler())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No handler registered");
        assertThatThrownBy(() -> new JobHandlerRegistry(
                List.of(new TextProcessingHandler(), new TextProcessingHandler())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("More than one handler");
    }
}
