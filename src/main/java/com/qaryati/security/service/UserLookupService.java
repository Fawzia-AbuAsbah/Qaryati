package com.qaryati.security.service;

import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import com.qaryati.security.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserLookupService implements UserLookup {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserSummary> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new UserSummary(user.getId(), user.isEnabled()));
    }
}
