package com.rag.adapter.inbound.dto.response;

import com.rag.domain.identity.model.User;
import java.util.UUID;

public record UserResponse(
    UUID userId, String username, String displayName, String email,
    String bu, String team, String role
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getUserId(), u.getUsername(), u.getDisplayName(),
            u.getEmail(), u.getBu(), u.getTeam(), u.getRole().name());
    }
}
