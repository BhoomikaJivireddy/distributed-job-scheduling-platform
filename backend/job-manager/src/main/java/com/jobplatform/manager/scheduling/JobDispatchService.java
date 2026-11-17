package com.jobplatform.manager.scheduling;

import com.jobplatform.common.domain.Job;
import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.repository.JobRepository;
import com.jobplatform.manager.config.ManagerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobDispatchService {

    private final JobRepository jobRepository;
    private final ManagerProperties properties;
    private final Clock clock;

    public JobDispatchService(JobRepository jobRepository, ManagerProperties properties, Clock clock) {
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<DispatchTicket> reserveDueJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minusSeconds(properties.queuedStaleSeconds());

        List<Job> dueJobs = jobRepository.lockDispatchable(now, staleBefore, properties.dispatchBatchSize());
        List<DispatchTicket> tickets = new ArrayList<>(dueJobs.size());
        for (Job job : dueJobs) {
            boolean redispatch = job.getStatus() == JobStatus.QUEUED;
            if (redispatch) {
                job.refreshQueuedAt(now);
            } else {
                job.markQueued(now);
            }
            tickets.add(new DispatchTicket(job.getId(), job.getPriority(), redispatch));
        }
        return tickets;
    }
}
