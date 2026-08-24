/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Apache 2.0 license (see https://www.apache.org/licenses/LICENSE-2.0).
 */
package io.redlink.more.data.configuration;

import io.redlink.more.data.garmin.wellness.ApiClient;
import io.redlink.more.data.garmin.wellness.client.UserApiApi;
import io.redlink.more.data.garmin.wellness.client.UserControllerApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class GarminWellnessApiConfiguration {
    @Bean
    @RequestScope
    public UserApiApi userApiApi() {
        return new UserApiApi(new ApiClient());
    }

    @Bean
    @RequestScope
    public UserControllerApi userControllerApi() {
        return new UserControllerApi(new ApiClient());
    }
}
