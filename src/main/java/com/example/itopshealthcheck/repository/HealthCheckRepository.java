package com.example.itopshealthcheck.repository;

import com.example.itopshealthcheck.model.HealthCheck;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthCheckRepository extends MongoRepository<HealthCheck, String> {
}