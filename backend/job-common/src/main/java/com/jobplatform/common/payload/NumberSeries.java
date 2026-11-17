package com.jobplatform.common.payload;

import java.math.BigDecimal;

public final class NumberSeries {

    private static final int MAX_VALUES = 5000;

    private NumberSeries() {
    }

    public static double[] parse(String payload) {
        String trimmed = payload.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidPayloadException("DATA_PROCESSING payload must contain at least one number");
        }

        String[] tokens = trimmed.split("[,;\\s]+");
        if (tokens.length > MAX_VALUES) {
            throw new InvalidPayloadException("DATA_PROCESSING payload may contain at most " + MAX_VALUES + " numbers");
        }

        double[] values = new double[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            values[index] = parseNumber(tokens[index]);
        }
        return values;
    }

    private static double parseNumber(String token) {
        try {
            return new BigDecimal(token).doubleValue();
        } catch (NumberFormatException exception) {
            String shown = token.length() > 30 ? token.substring(0, 30) + "..." : token;
            throw new InvalidPayloadException("DATA_PROCESSING payload contains a non-numeric value: '" + shown + "'");
        }
    }
}
