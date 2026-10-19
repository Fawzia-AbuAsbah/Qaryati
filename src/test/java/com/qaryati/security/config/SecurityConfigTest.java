
package com.qaryati.security.config;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")

@Import(SecurityConfigTest.TestController.class)

class SecurityConfigTest {

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {

        MockMvc mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(get("/api/v1/test/secure"))
                .andExpect(status().isUnauthorized());
    }



    @Test
    void shouldAllowUserWithValidJwt() throws Exception {

        String username = "test_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                passwordEncoder.encode("CorrectPassword123!"),
                "Test User"
        );

        userRepository.saveAndFlush(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of()
                );

        String token = jwtService.generateToken(authentication);

        MockMvc mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(get("/api/v1/test/secure")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }


    @Test
    void shouldRejectIncorrectPassword() throws Exception {
        String username = "test_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                passwordEncoder.encode("CorrectPassword123!"),
                "Test User"
        );

        userRepository.saveAndFlush(user);

        MockMvc mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(get("/api/v1/test/secure")
                        .with(httpBasic(username, "WrongPassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDisabledUser() throws Exception {
        String username = "test_" + UUID.randomUUID()
                .toString().substring(0, 8);

        User user = new User(
                username,
                username + "@example.com",
                passwordEncoder.encode("CorrectPassword123!"),
                "Disabled User"
        );

        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        MockMvc mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(get("/api/v1/test/secure")
                        .with(httpBasic(username, "CorrectPassword123!")))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test/secure")
        ResponseEntity<String> secureEndpoint() {
            return ResponseEntity.ok("Protected content");
        }
    }
}
