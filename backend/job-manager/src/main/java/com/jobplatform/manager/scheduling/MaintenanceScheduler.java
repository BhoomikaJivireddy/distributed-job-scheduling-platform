package com.jobplatform.manager.scheduling;

import com.jobplatform.manager.service.WorkerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceScheduler {

    private final LeaseRecoveryService leaseRecoveryService;
    private final WorkerService workerService;

    public MaintenanceScheduler(LeaseRecoveryService leaseRecoveryService, WorkerService workerService) {
        this.leaseRecoveryService = leaseRecoveryService;
        this.workerService = workerService;
    }

    @Scheduled(fixedDelayString = "${platform.manager.lease-recovery-interval-ms}")
    public void recoverExpiredLeases() {
        leaseRecoveryService.recoverExpiredLeases();
    }

    @Scheduled(fixedDelayString = "${platform.manager.liveness-interval-ms}")
    public void markStaleWorkersInactive() {
        workerService.markStaleWorkersInactive();
    }
}
