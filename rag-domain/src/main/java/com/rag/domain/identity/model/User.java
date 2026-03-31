package com.rag.domain.identity.model;

import java.time.Instant;
import java.util.UUID;

public class User {
    private UUID userId;
    private String username;
    private String displayName;
    private String email;
    private String bu;
    private String team;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {}

    public User(UUID userId, String username, String displayName, String email,
                String bu, String team, Role role, UserStatus status,
                Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.bu = bu;
        this.team = team;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBu() { return bu; }
    public void setBu(String bu) { this.bu = bu; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
