package com.rag.adapter.outbound.persistence.mapper;

import com.rag.adapter.outbound.persistence.entity.UserEntity;
import com.rag.domain.identity.model.Role;
import com.rag.domain.identity.model.User;
import com.rag.domain.identity.model.UserStatus;

public class UserMapper {

    public static User toDomain(UserEntity e) {
        return new User(e.getUserId(), e.getUsername(), e.getDisplayName(), e.getEmail(),
            e.getBu(), e.getTeam(), Role.valueOf(e.getRole()),
            UserStatus.valueOf(e.getStatus()), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static UserEntity toEntity(User u) {
        UserEntity e = new UserEntity();
        e.setUserId(u.getUserId());
        e.setUsername(u.getUsername());
        e.setDisplayName(u.getDisplayName());
        e.setEmail(u.getEmail());
        e.setBu(u.getBu());
        e.setTeam(u.getTeam());
        e.setRole(u.getRole().name());
        e.setStatus(u.getStatus().name());
        e.setCreatedAt(u.getCreatedAt());
        e.setUpdatedAt(u.getUpdatedAt());
        return e;
    }
}
