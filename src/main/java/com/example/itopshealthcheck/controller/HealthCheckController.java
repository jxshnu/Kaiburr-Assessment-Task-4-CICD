package com.example.itopshealthcheck.controller;

import com.example.itopshealthcheck.model.HealthCheck;
import com.example.itopshealthcheck.service.HealthCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/health-checks")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    public HealthCheckController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping
    public ResponseEntity<List<HealthCheck>> getAllHealthChecks() {
        return ResponseEntity.ok(healthCheckService.getAllHealthChecks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HealthCheck> getHealthCheckById(@PathVariable String id) {
        return ResponseEntity.ok(healthCheckService.getHealthCheckById(id));
    }

    @PutMapping
    public ResponseEntity<HealthCheck> createHealthCheck(@RequestBody HealthCheck healthCheck) {
        HealthCheck createdHealthCheck = healthCheckService.createHealthCheck(healthCheck);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdHealthCheck.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdHealthCheck);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHealthCheck(@PathVariable String id) {
        healthCheckService.deleteHealthCheck(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/run")
    public ResponseEntity<HealthCheck> runHealthCheck(@PathVariable String id) {
        // In a real application, the user would be extracted from the security context
        String triggeredBy = "api-user";
        HealthCheck updatedHealthCheck = healthCheckService.runHealthCheck(id, triggeredBy);
        return ResponseEntity.ok(updatedHealthCheck);
    }
}