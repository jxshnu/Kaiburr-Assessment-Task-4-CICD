package com.example.itopshealthcheck.controller;

import com.example.itopshealthcheck.dto.HealthCheckRequest;
import com.example.itopshealthcheck.model.HealthCheck;
import com.example.itopshealthcheck.service.HealthCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/health-checks")
@CrossOrigin(origins = "*")
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

    // This endpoint now accepts the HealthCheckRequest DTO instead of the HealthCheck entity.
    @PutMapping
    public ResponseEntity<HealthCheck> createHealthCheck(@RequestBody HealthCheckRequest healthCheckRequest) {
        HealthCheck createdHealthCheck = healthCheckService.createHealthCheck(healthCheckRequest);
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
        String triggeredBy = "api-user";
        HealthCheck updatedHealthCheck = healthCheckService.runHealthCheck(id, triggeredBy);
        return ResponseEntity.ok(updatedHealthCheck);
    }
}
