package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TextProcessingHandler implements JobHandler {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}']+");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+");
    private static final int TOP_WORD_COUNT = 5;

    @Override
    public JobType jobType() {
        return JobType.TEXT_PROCESSING;
    }

    @Override
    public JobResult execute(JobContext context) {
        String text = context.payload();

        Map<String, Integer> frequencies = new HashMap<>();
        int wordCount = 0;
        Matcher words = WORD.matcher(text);
        while (words.find()) {
            wordCount++;
            frequencies.merge(words.group().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        int sentenceCount = 0;
        Matcher sentences = SENTENCE_END.matcher(text);
        while (sentences.find()) {
            sentenceCount++;
        }
        if (sentenceCount == 0 && wordCount > 0) {
            sentenceCount = 1;
        }

        String longestWord = frequencies.keySet().stream()
                .max(Comparator.comparingInt(String::length).thenComparing(Comparator.reverseOrder()))
                .orElse("");

        List<String> topWords = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_WORD_COUNT)
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .toList();

        String summary = String.join("\n",
                "characters=" + text.length(),
                "words=" + wordCount,
                "lines=" + text.lines().count(),
                "sentences=" + sentenceCount,
                "unique_words=" + frequencies.size(),
                "longest_word=" + longestWord,
                "top_words=" + topWords.stream().collect(Collectors.joining(",")));
        return new JobResult(summary);
    }
}
