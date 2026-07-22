package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finguide.security.demo-mode=false")
@AutoConfigureMockMvc
class RegistrationControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KeycloakRegistrationService registrationService;

    @Test
    void registersUserWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Стас",
                                  "lastName": "Воронков",
                                  "email": "stas@example.com",
                                  "password": "correct-horse-battery"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("stas@example.com"));

        ArgumentCaptor<RegistrationRequest> request = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(registrationService).register(request.capture());
        assertThat(request.getValue().firstName()).isEqualTo("Стас");
        assertThat(request.getValue().lastName()).isEqualTo("Воронков");
        assertThat(request.getValue().email()).isEqualTo("stas@example.com");
        assertThat(request.getValue().password()).isEqualTo("correct-horse-battery");
    }

    @Test
    void acceptsPasswordResetRequestWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "stas@example.com"
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<PasswordResetRequest> request = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(registrationService).requestPasswordReset(request.capture());
        assertThat(request.getValue().email()).isEqualTo("stas@example.com");
    }

    @Test
    void rejectsInvalidPasswordResetEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }
}
