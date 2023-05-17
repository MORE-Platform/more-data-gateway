package io.redlink.more.data.service;

import io.redlink.more.data.model.ApiRoutingInfo;
import io.redlink.more.data.repository.StudyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ExternalService {
    private final StudyRepository repository;

    public ExternalService(StudyRepository repository) {
        this.repository = repository;
    }
    public ApiRoutingInfo getRoutingInfo(String apiToken, String participantId) {
        return repository.getApiRoutingInfo(
                apiToken,
                participantId);
    }
}
