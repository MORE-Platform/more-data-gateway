package io.redlink.more.data.service.observations.limesurvey.config;

import io.redlink.more.data.limesurvey.client.LimeSurveyRcApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class LimeSurveyConfiguration {

    private final LimeSurveyProperties limeSurveyProperties;

    public LimeSurveyConfiguration(LimeSurveyProperties limeSurveyProperties) {
        this.limeSurveyProperties = limeSurveyProperties;
    }

    @Bean
    public LimeSurveyRcApi limeSurveyRcApi() {
        LimeSurveyRcApi api = new LimeSurveyRcApi();
        if (StringUtils.hasText(limeSurveyProperties.getBaseUrl())) {
            String defaultBasePath = api.getApiClient().getBasePath();
            String path = UriComponentsBuilder.fromUriString(defaultBasePath).build().getPath();
            String newBasePath = UriComponentsBuilder.fromUriString(limeSurveyProperties.getBaseUrl())
                    .path(path)
                    .build().toUriString();
            api.getApiClient().setBasePath(newBasePath);
        }
        return api;
    }
}
