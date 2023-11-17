/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.configuration;

import io.redlink.more.data.properties.GatewayUserDatabaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties({GatewayUserDatabaseProperties.class})
public class DatabaseConfiguration {

    private final GatewayUserDatabaseProperties gatewayUserDatabaseProperties;

    DatabaseConfiguration(GatewayUserDatabaseProperties gatewayUserDatabaseProperties) {
        this.gatewayUserDatabaseProperties = gatewayUserDatabaseProperties;
    }

    @Bean
    public DataSource postgreSqlDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(this.gatewayUserDatabaseProperties.getUrl());
        dataSource.setUsername(this.gatewayUserDatabaseProperties.getUsername());
        dataSource.setPassword(this.gatewayUserDatabaseProperties.getPassword());

        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

}
