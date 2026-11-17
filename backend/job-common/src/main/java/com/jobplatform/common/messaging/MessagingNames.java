package com.jobplatform.common.messaging;

public final class MessagingNames {

    public static final String JOB_EXCHANGE = "jobs.exchange";
    public static final String JOB_QUEUE = "jobs.queue";
    public static final String JOB_ROUTING_KEY = "job.execute";

    public static final String DEAD_LETTER_EXCHANGE = "jobs.dlx";
    public static final String DEAD_LETTER_QUEUE = "jobs.dead.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "job.dead";

    public static final int MAX_PRIORITY = 10;

    private MessagingNames() {
    }
}
