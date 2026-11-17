package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.payload.NumberSeries;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class DataProcessingHandler implements JobHandler {

    @Override
    public JobType jobType() {
        return JobType.DATA_PROCESSING;
    }

    @Override
    public JobResult execute(JobContext context) {
        double[] values = NumberSeries.parse(context.payload());
        double[] sorted = values.clone();
        Arrays.sort(sorted);

        double sum = Arrays.stream(values).sum();
        double mean = sum / values.length;
        double variance = Arrays.stream(values).map(value -> (value - mean) * (value - mean)).sum() / values.length;

        String summary = String.join("\n",
                "count=" + values.length,
                "sum=" + format(sum),
                "min=" + format(sorted[0]),
                "max=" + format(sorted[sorted.length - 1]),
                "mean=" + format(mean),
                "median=" + format(median(sorted)),
                "std_dev=" + format(Math.sqrt(variance)));
        return new JobResult(summary);
    }

    private static double median(double[] sorted) {
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }
        return (sorted[middle - 1] + sorted[middle]) / 2;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
