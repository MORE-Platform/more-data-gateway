package io.redlink.more.data.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableCaching
@EnableScheduling
public class CachingConfiguration {

    public static final String OBSERVATION_ENDINGS = "observationEndings";
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingConfiguration.class);
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(OBSERVATION_ENDINGS);
    }

    @CacheEvict(allEntries = true, value = {OBSERVATION_ENDINGS})
    @Scheduled(fixedDelay = 60 * 60 * 1000 ,  initialDelay = 5000)
    public void reportCacheEvict() {
        LOGGER.info("Flush Cache");
    }
}
