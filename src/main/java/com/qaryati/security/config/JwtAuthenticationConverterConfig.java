
package com.qaryati.security.config;

import com.qaryati.security.service.DatabaseUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;

@Configuration
public class JwtAuthenticationConverterConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            DatabaseUserDetailsService userDetailsService) {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            UserDetails user;
            try {
                user = userDetailsService.loadUserByUsername(jwt.getSubject());
            } catch (UsernameNotFoundException ex) {
                throw new InvalidBearerTokenException("Invalid bearer token");
            }

            if (!user.isEnabled()) {
                throw new InvalidBearerTokenException("Invalid bearer token");
            }

            return new ArrayList<GrantedAuthority>(user.getAuthorities());
        });

        return converter;
    }
}
