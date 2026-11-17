package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.payload.CsvTable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ReportGenerationHandler implements JobHandler {

    @Override
    public JobType jobType() {
        return JobType.REPORT_GENERATION;
    }

    @Override
    public JobResult execute(JobContext context) {
        CsvTable table = CsvTable.parse(context.payload());

        List<String> lines = new ArrayList<>();
        lines.add("rows=" + table.rows().size());
        lines.add("columns=" + table.header().size());
        for (int index = 0; index < table.header().size(); index++) {
            lines.add(describeColumn(table.header().get(index), table.column(index)));
        }
        return new JobResult(String.join("\n", lines));
    }

    private static String describeColumn(String name, List<String> values) {
        List<BigDecimal> numbers = parseAllNumbers(values);
        if (numbers != null) {
            return describeNumeric(name, numbers);
        }
        return describeText(name, values);
    }

    private static List<BigDecimal> parseAllNumbers(List<String> values) {
        List<BigDecimal> numbers = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                numbers.add(new BigDecimal(value));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return numbers;
    }

    private static String describeNumeric(String name, List<BigDecimal> numbers) {
        BigDecimal sum = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal min = numbers.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = numbers.stream().max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal average = sum.divide(BigDecimal.valueOf(numbers.size()), 2, RoundingMode.HALF_UP);
        return "column=" + name + " type=numeric sum=" + sum.toPlainString() + " min=" + min.toPlainString()
                + " max=" + max.toPlainString() + " avg=" + average.toPlainString();
    }

    private static String describeText(String name, List<String> values) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        Map.Entry<String, Integer> mostCommon = counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .orElseThrow();
        return "column=" + name + " type=text distinct=" + counts.size()
                + " most_common=" + mostCommon.getKey() + "(" + mostCommon.getValue() + ")";
    }
}
