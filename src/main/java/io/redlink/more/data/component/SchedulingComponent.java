package io.redlink.more.data.component;

import io.redlink.more.data.properties.GarminProperties;
import io.redlink.more.data.service.garmin.GarminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SchedulingComponent {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulingComponent.class);
    private final TaskScheduler scheduler;
    private final GarminService garminService;
    private final GarminProperties garminProperties;

    public SchedulingComponent(TaskScheduler scheduler, GarminService garminService, GarminProperties garminProperties) {
        this.scheduler = scheduler;
        this.garminService = garminService;
        this.garminProperties = garminProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    protected void initTokenRefresh() {
        LOG.info("Initializing Garmin token refresh task (cron: {})", garminProperties.tokenRefresh());
        scheduler.schedule(this::refreshAllGarminTokens, garminProperties.tokenRefresh());
    }

    private void refreshAllGarminTokens() {
        LOG.info("Refresh Garmin Tokens for {}", Instant.now().truncatedTo(ChronoUnit.MINUTES).toString());
        garminService.refreshAllTokens();
    }
}
