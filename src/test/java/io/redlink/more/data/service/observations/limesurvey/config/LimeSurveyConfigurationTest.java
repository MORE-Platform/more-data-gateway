package io.redlink.more.data.service.observations.limesurvey.config;

import io.redlink.more.data.limesurvey.client.LimeSurveyRcApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LimeSurveyConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(LimeSurveyConfiguration.class, LimeSurveyProperties.class);

    @Test
    void testLimeSurveyRcApiBean() {
        contextRunner
                .withPropertyValues(
                        "more.limesurvey.username=admin",
                        "more.limesurvey.password=admin",
                        "more.limesurvey.baseUrl=https://lime.example.com/index.php"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LimeSurveyRcApi.class);
                    LimeSurveyRcApi api = context.getBean(LimeSurveyRcApi.class);
                    assertThat(api.getApiClient().getBasePath())
                            .isEqualTo("https://lime.example.com/index.php/admin/remotecontrol");
                });
    }

    @Test
    void testLimeSurveyRcApiBeanWithTrailingSlash() {
        contextRunner
                .withPropertyValues(
                        "more.limesurvey.username=admin",
                        "more.limesurvey.password=admin",
                        "more.limesurvey.baseUrl=https://lime.example.com/index.php"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LimeSurveyRcApi.class);
                    LimeSurveyRcApi api = context.getBean(LimeSurveyRcApi.class);
                    assertThat(api.getApiClient().getBasePath())
                            .isEqualTo("https://lime.example.com/index.php/admin/remotecontrol");
                });
    }

    @Test
    void testLimeSurveyRcApiBeanWithSubdir() {
        contextRunner
                .withPropertyValues(
                        "more.limesurvey.username=admin",
                        "more.limesurvey.password=admin",
                        "more.limesurvey.baseUrl=https://lime.example.com/index.php/limesurvey"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(LimeSurveyRcApi.class);
                    LimeSurveyRcApi api = context.getBean(LimeSurveyRcApi.class);
                    assertThat(api.getApiClient().getBasePath())
                            .isEqualTo("https://lime.example.com/index.php/limesurvey/admin/remotecontrol");
                });
    }
}
