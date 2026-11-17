package com.jobplatform.manager.scheduling;

import com.jobplatform.manager.messaging.JobMessagePublisher;
import com.jobplatform.manager.messaging.MessagePublishException;
import com.jobplatform.manager.metrics.ManagerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDispatcherTest {

    @Mock
    private JobDispatchService dispatchService;

    @Mock
    private JobMessagePublisher publisher;

    private SimpleMeterRegistry registry;
    private JobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        dispatcher = new JobDispatcher(dispatchService, publisher, new ManagerMetrics(registry));
    }

    @Test
    void everyReservedJobIsPublishedWithItsPriority() {
        when(dispatchService.reserveDueJobs()).thenReturn(List.of(new DispatchTicket(1L, 9, false), new DispatchTicket(2L, 4, true)));

        dispatcher.dispatchDueJobs();

        verify(publisher).publish(1L, 9);
        verify(publisher).publish(2L, 4);
        assertThat(registry.counter("jobs.dispatched").count()).isEqualTo(2.0);
    }

    @Test
    void aFailedPublishDoesNotStopTheRemainingJobsAndIsCounted() {
        when(dispatchService.reserveDueJobs()).thenReturn(List.of(new DispatchTicket(1L, 5, false), new DispatchTicket(2L, 5, false)));
        doThrow(new MessagePublishException("no confirm")).when(publisher).publish(1L, 5);

        dispatcher.dispatchDueJobs();

        verify(publisher).publish(2L, 5);
        assertThat(registry.counter("jobs.dispatch.failures").count()).isEqualTo(1.0);
        assertThat(registry.counter("jobs.dispatched").count()).isEqualTo(1.0);
    }
}
