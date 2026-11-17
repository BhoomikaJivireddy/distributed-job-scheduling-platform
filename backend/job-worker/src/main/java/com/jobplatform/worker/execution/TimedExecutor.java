package com.jobplatform.worker.execution;

import com.jobplatform.worker.config.WorkerProperties;
import com.jobplatform.worker.handler.JobContext;
import com.jobplatform.worker.handler.JobHandler;
import com.jobplatform.worker.handler.JobResult;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TimedExecutor {

    private final ExecutorService executor;

    public TimedExecutor(WorkerProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(properties.concurrency(), 1) * 2, runnable -> {
            Thread thread = new Thread(runnable, "job-handler-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public JobResult execute(JobHandler handler, JobContext context, Duration timeout) throws Exception {
        Future<JobResult> future = executor.submit(() -> handler.execute(context));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception handlerFailure) {
                throw handlerFailure;
            }
            throw exception;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
