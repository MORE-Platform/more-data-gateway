/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Österreichische Vereinigung zur
 * Förderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConfigurationProperties(prefix = "more.login-token")
public class LoginTokenProperties {

    private String hashAlgorithm = "SHA-256";

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    @PostConstruct
    public void validateConfiguration() {
        try {
            MessageDigest.getInstance(getHashAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid hash algorithm: " + getHashAlgorithm(), e);
        }
    }
}
