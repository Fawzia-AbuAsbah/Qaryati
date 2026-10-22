
package com.qaryati.security.service;

import com.qaryati.security.entity.Role;
import com.qaryati.security.entity.User;
import com.qaryati.security.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = userRepository.findWithRolesByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        var authorities = user.getRoles().stream()
                .map(Role::getCode)
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
