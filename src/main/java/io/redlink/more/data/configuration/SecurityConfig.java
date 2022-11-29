package io.redlink.more.data.configuration;

import io.redlink.more.data.service.GatewayUserDetailService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

@Configuration
public class SecurityConfig {

    private final GatewayUserDetailService gatewayUserDetailService;

    SecurityConfig(GatewayUserDetailService gatewayUserDetailService) {
        this.gatewayUserDetailService = gatewayUserDetailService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationProvider authenticationProvider) throws Exception {

        http.csrf().disable()
                .authenticationProvider(authenticationProvider)
                .authorizeRequests(req -> {
                    // root for smoke-tests is allowed
                    req.antMatchers("/")
                            .permitAll();
                    // registration-endpoints needs to be open
                    req.antMatchers("/api/v1/registration")
                            .permitAll();
                    // all other apis require credentials
                    req.antMatchers("/api/v1/**")
                            .authenticated();
                    // actuator only from localhost
                    req.antMatchers("/actuator/**")
                            .hasIpAddress("127.0.0.1/8");
                    // everything else is denied
                    req.anyRequest().denyAll();
                })
                .httpBasic().realmName("MORE");

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(gatewayUserDetailService);
        return provider;
    }

    @Bean
    @SuppressWarnings("deprecation")
    public PasswordEncoder passwordEncoder() {
        final String encodingId = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(encodingId, new BCryptPasswordEncoder());
        encoders.put("noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance());
        encoders.put(null, org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance());
        encoders.put("pbkdf2", new Pbkdf2PasswordEncoder());
        encoders.put("scrypt", new SCryptPasswordEncoder());
        encoders.put("argon2", new Argon2PasswordEncoder());
        return new DelegatingPasswordEncoder(encodingId, encoders);
    }

    @Bean
    protected RequestRejectedHandler requestRejectedHandler() {
        // Use a specific status-code for the Firewall to identify denied requests
        return new HttpStatusRequestRejectedHandler(HttpStatus.I_AM_A_TEAPOT.value());
    }

}