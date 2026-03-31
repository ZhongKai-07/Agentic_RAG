package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.UserMapper;
import com.rag.adapter.outbound.persistence.repository.UserJpaRepository;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.port.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return jpa.findById(userId).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    public User save(User user) {
        return UserMapper.toDomain(jpa.save(UserMapper.toEntity(user)));
    }
}
