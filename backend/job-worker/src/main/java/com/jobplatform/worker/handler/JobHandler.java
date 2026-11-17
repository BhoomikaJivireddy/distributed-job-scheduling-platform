package com.jobplatform.worker.handler;

import com.jobplatform.common.domain.JobType;

public interface JobHandler {

    JobType jobType();

    JobResult execute(JobContext context) throws Exception;
}
