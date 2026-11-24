package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.entity.AgriculturalProduct;
import com.qaryati.agriculture.entity.AgriculturalProductType;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.RoleRepository;
import com.qaryati.security.repository.UserRepository;
import com.qaryati.security.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgriculturalProductAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AgriculturalProductRepository productRepository;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String tokenForRole(String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        String username = "product_auth_" + UUID.randomUUID().toString().replace("-", "");
        User user = new User(username, username + "@example.com", "test-password-hash", "Test User");
        user.addRole(role);
        userRepository.saveAndFlush(user);
        return jwtService.generateToken(new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private Long productId() {
        AgriculturalProduct product = new AgriculturalProduct();
        product.setNameAr("Product " + UUID.randomUUID());
        product.setProductType(AgriculturalProductType.CROP);
        return productRepository.saveAndFlush(product).getId();
    }

    private String requestBody(String name) {
        return """
                {"nameAr":"%s","nameEn":"Product","productType":"CROP"}
                """.formatted(name);
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc().perform(get("/api/v1/agricultural-products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        String token = tokenForRole("VIEWER");

        mockMvc().perform(get("/api/v1/agricultural-products/" + productId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc().perform(post("/api/v1/agricultural-products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Viewer Product")))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorCanCreateAndUpdateButCannotDelete() throws Exception {
        String token = tokenForRole("EDITOR");
        Long existingId = productId();

        mockMvc().perform(post("/api/v1/agricultural-products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Editor Product " + UUID.randomUUID())))
                .andExpect(status().isCreated());

        mockMvc().perform(put("/api/v1/agricultural-products/" + existingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Updated Product " + UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc().perform(delete("/api/v1/agricultural-products/" + existingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDelete() throws Exception {
        String token = tokenForRole("ADMIN");

        mockMvc().perform(delete("/api/v1/agricultural-products/" + productId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
