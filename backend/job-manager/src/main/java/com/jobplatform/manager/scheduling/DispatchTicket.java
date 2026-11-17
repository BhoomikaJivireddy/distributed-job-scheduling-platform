package com.jobplatform.manager.scheduling;

public record DispatchTicket(Long jobId, int priority, boolean redispatch) {
}
