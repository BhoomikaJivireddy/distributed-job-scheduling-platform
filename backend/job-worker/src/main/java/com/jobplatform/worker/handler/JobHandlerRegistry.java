package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JobHandlerRegistry {

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry(List<JobHandler> handlerBeans) {
        for (JobHandler handler : handlerBeans) {
            if (handlers.put(handler.jobType(), handler) != null) {
                throw new IllegalStateException("More than one handler registered for " + handler.jobType());
            }
        }
        for (JobType jobType : JobType.values()) {
            if (!handlers.containsKey(jobType)) {
                throw new IllegalStateException("No handler registered for " + jobType);
            }
        }
    }

    public Optional<JobHandler> find(JobType jobType) {
        return Optional.ofNullable(handlers.get(jobType));
    }
}
