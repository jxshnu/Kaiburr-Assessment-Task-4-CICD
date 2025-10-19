package com.example.itopshealthcheck.dto;

// This is a Data Transfer Object (DTO).
// It's a best practice to use DTOs for incoming API requests
// instead of exposing the internal database entities directly.
// This fixes the vulnerability flagged by SonarCloud.
public class HealthCheckRequest {
    private String name;
    private String owner;
    private String command;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
