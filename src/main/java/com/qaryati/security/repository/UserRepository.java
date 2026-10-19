
package com.qaryati.security.repository;

import com.qaryati.security.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);
}
