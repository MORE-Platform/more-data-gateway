package io.redlink.more.data.configuration;

import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties({SchedulerProperties.class})
public class SchedulerConfiguration {

    public static final Integer DEFAULT_POOL_SIZE = 1;

    SchedulerProperties conf;

    SchedulerConfiguration(SchedulerProperties schedulerProperties) {
        this.conf = schedulerProperties;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        var size = conf.getPoolSize() <= 1 ? DEFAULT_POOL_SIZE : conf.getPoolSize();
        LoggerFactory.getLogger(SchedulerConfiguration.class).debug("Create TaskScheduler with poolSize {}", size);

        final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(size);
        return taskScheduler;
    }

}
