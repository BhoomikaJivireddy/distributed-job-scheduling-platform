package com.jobplatform.common.payload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record CsvTable(List<String> header, List<List<String>> rows) {

    public static CsvTable parse(String payload) {
        List<String> lines = payload.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.size() < 2) {
            throw new InvalidPayloadException(
                    "REPORT_GENERATION payload must be CSV text with a header row and at least one data row");
        }

        List<String> header = splitRow(lines.get(0));
        if (header.stream().anyMatch(String::isEmpty)) {
            throw new InvalidPayloadException("REPORT_GENERATION header contains an empty column name");
        }

        List<List<String>> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            List<String> row = splitRow(lines.get(index));
            if (row.size() != header.size()) {
                throw new InvalidPayloadException("REPORT_GENERATION row " + index + " has " + row.size()
                        + " columns but the header has " + header.size());
            }
            rows.add(row);
        }
        return new CsvTable(header, rows);
    }

    public List<String> column(int index) {
        return rows.stream().map(row -> row.get(index)).toList();
    }

    private static List<String> splitRow(String line) {
        return Arrays.stream(line.split(",", -1)).map(String::strip).toList();
    }
}
