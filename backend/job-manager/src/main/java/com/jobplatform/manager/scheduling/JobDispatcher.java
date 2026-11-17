package com.jobplatform.manager.scheduling;

import com.jobplatform.manager.messaging.JobMessagePublisher;
import com.jobplatform.manager.messaging.MessagePublishException;
import com.jobplatform.manager.metrics.ManagerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private final JobDispatchService dispatchService;
    private final JobMessagePublisher publisher;
    private final ManagerMetrics metrics;

    public JobDispatcher(JobDispatchService dispatchService, JobMessagePublisher publisher, ManagerMetrics metrics) {
        this.dispatchService = dispatchService;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${platform.manager.dispatch-interval-ms}")
    public void dispatchDueJobs() {
        List<DispatchTicket> tickets = dispatchService.reserveDueJobs();
        for (DispatchTicket ticket : tickets) {
            publish(ticket);
        }
    }

    private void publish(DispatchTicket ticket) {
        try {
            publisher.publish(ticket.jobId(), ticket.priority());
            metrics.jobDispatched();
            if (ticket.redispatch()) {
                log.warn("Job {} was queued but never claimed; published again", ticket.jobId());
            } else {
                log.info("Job {} published to the job queue", ticket.jobId());
            }
        } catch (MessagePublishException exception) {
            metrics.dispatchFailed();
            log.error("Job {} stays queued and will be published again after the stale window: {}",
                    ticket.jobId(), exception.getMessage());
        }
    }
}
