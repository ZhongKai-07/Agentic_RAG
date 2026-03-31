package com.rag.domain.identity.port;

import com.rag.domain.identity.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID userId);
    Optional<User> findByUsername(String username);
    User save(User user);
}
