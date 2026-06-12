package les13.finguide.backend.auth;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KeycloakAdminClientConfig {
    @Bean
    RestTemplate keycloakAdminRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
