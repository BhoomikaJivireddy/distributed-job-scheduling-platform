package com.jobplatform.common.payload;

import com.jobplatform.common.domain.JobType;

public final class PayloadValidator {

    private PayloadValidator() {
    }

    public static void validate(JobType jobType, String payload) {
        switch (jobType) {
            case TEXT_PROCESSING -> {
                if (payload.isBlank()) {
                    throw new InvalidPayloadException("TEXT_PROCESSING payload must contain text");
                }
            }
            case REPORT_GENERATION -> CsvTable.parse(payload);
            case DATA_PROCESSING -> NumberSeries.parse(payload);
            case FAILURE_SIMULATION -> SimulationSettings.parse(payload);
        }
    }
}
