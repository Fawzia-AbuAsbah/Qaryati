package com.qaryati.security.api;

import java.util.Optional;

public interface UserLookup {
    Optional<UserSummary> findByUsername(String username);
}
