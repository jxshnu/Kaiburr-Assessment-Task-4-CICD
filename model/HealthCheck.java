package com.example.itopshealthcheck.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "health_checks")
public class HealthCheck {
    @Id
    private String id;
    private String name;
    private String owner;
    private String command;
    private List<ExecutionLog> executionLogs = new ArrayList<>();
}