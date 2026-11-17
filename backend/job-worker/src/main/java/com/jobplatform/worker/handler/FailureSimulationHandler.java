package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;
import com.jobplatform.common.payload.SimulationSettings;
import org.springframework.stereotype.Component;

@Component
public class FailureSimulationHandler implements JobHandler {

    @Override
    public JobType jobType() {
        return JobType.FAILURE_SIMULATION;
    }

    @Override
    public JobResult execute(JobContext context) throws InterruptedException {
        SimulationSettings settings = SimulationSettings.parse(context.payload());

        if (settings.delayMillis() > 0) {
            Thread.sleep(settings.delayMillis());
        }
        if (context.attemptNumber() <= settings.failures()) {
            throw new IllegalStateException("simulated failure on attempt " + context.attemptNumber());
        }
        return new JobResult("succeeded on attempt " + context.attemptNumber()
                + " after " + settings.failures() + " simulated failures");
    }
}
