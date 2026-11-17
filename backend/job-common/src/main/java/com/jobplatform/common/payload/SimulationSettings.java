package com.jobplatform.common.payload;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public record SimulationSettings(int failures, long delayMillis) {

    private static final int MAX_FAILURES = 10;
    private static final long MAX_DELAY_MILLIS = 600_000;

    public static SimulationSettings parse(String payload) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(payload));
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidPayloadException("FAILURE_SIMULATION payload must be key=value lines");
        }
        int failures = (int) readNumber(properties, "failures", MAX_FAILURES);
        long delayMillis = readNumber(properties, "delay_ms", MAX_DELAY_MILLIS);
        return new SimulationSettings(failures, delayMillis);
    }

    private static long readNumber(Properties properties, String key, long max) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return 0;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0 || value > max) {
                throw new InvalidPayloadException("FAILURE_SIMULATION " + key + " must be between 0 and " + max);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new InvalidPayloadException("FAILURE_SIMULATION " + key + " must be a whole number");
        }
    }
}
