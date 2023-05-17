package io.redlink.more.data.service;

import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ExternalService {
    private final StudyRepository repository;
    private final PasswordEncoder passwordEncoder;

    public ExternalService(StudyRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }
    public ApiRoutingInfo getRoutingInfo(String apiToken, String participantId) {
        return repository.getApiRoutingInfo(
                passwordEncoder.encode(apiToken),
                participantId);
    }
}
