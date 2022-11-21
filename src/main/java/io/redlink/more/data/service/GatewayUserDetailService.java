package io.redlink.more.data.service;

import io.redlink.more.data.model.GatewayUserDetails;
import io.redlink.more.data.repository.GatewayUserRepository;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GatewayUserDetailService implements UserDetailsService {

    public static final String APP_ROLE = "APP";

    private final GatewayUserRepository gatewayUserRepository;

    GatewayUserDetailService(GatewayUserRepository gatewayUserRepository) {
        this.gatewayUserRepository = gatewayUserRepository;
    }

    @Override
    public GatewayUserDetails loadUserByUsername(String apiId) throws UsernameNotFoundException {
        final Optional<GatewayUserDetails> gatewayUserDetails = this.gatewayUserRepository.findByApiId(apiId, Set.of(APP_ROLE));

        return gatewayUserDetails.orElseThrow(
                () -> new UsernameNotFoundException(String.format("ApiId [%s] not found", apiId))
        );
    }

}
