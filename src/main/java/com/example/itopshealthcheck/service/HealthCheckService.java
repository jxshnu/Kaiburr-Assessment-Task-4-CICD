package com.example.itopshealthcheck.service;

import com.example.itopshealthcheck.exception.InvalidCommandException;
import com.example.itopshealthcheck.exception.ResourceNotFoundException;
import com.example.itopshealthcheck.model.ExecutionLog;
import com.example.itopshealthcheck.model.HealthCheck;
import com.example.itopshealthcheck.model.Status;
import com.example.itopshealthcheck.repository.HealthCheckRepository;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    private static final String K8S_NAMESPACE = "health-checks";
    private static final List<String> COMMAND_WHITELIST = Arrays.asList("ping", "nslookup", "wget", "echo");
    private static final Pattern SHELL_METACHARS_PATTERN = Pattern.compile("[;|`&><]");

    private final HealthCheckRepository healthCheckRepository;

    // These will only be injected if the 'kubernetes' profile is active
    @Autowired(required = false)
    private CoreV1Api coreV1Api;
    @Autowired(required = false)
    private BatchV1Api batchV1Api;


    public HealthCheckService(HealthCheckRepository healthCheckRepository) {
        this.healthCheckRepository = healthCheckRepository;
    }

    public List<HealthCheck> getAllHealthChecks() {
        return healthCheckRepository.findAll();
    }

    public HealthCheck getHealthCheckById(String id) {
        return healthCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HealthCheck not found with id: " + id));
    }

    public HealthCheck createHealthCheck(HealthCheck healthCheck) {
        validateCommand(healthCheck.getCommand());
        return healthCheckRepository.save(healthCheck);
    }

    public void deleteHealthCheck(String id) {
        if (!healthCheckRepository.existsById(id)) {
            throw new ResourceNotFoundException("HealthCheck not found with id: " + id);
        }
        healthCheckRepository.deleteById(id);
    }

    public HealthCheck runHealthCheck(String id, String triggeredBy) {
        // This check determines if we are in Kubernetes mode
        if (batchV1Api == null || coreV1Api == null) {
            logger.warn("Kubernetes client not available. Running in local mode. Command execution is skipped.");
            HealthCheck healthCheck = getHealthCheckById(id);
            ExecutionLog log = new ExecutionLog(triggeredBy);
            log.setStatus(Status.SUCCESS);
            log.setStartTime(new Date());
            log.setEndTime(new Date());
            log.setOutput("Command execution is disabled in local mode.");
            healthCheck.getExecutionLogs().add(log);
            return healthCheckRepository.save(healthCheck);
        }

        HealthCheck healthCheck = getHealthCheckById(id);
        ExecutionLog log = new ExecutionLog(triggeredBy);
        log.setStatus(Status.RUNNING);
        healthCheck.getExecutionLogs().add(log);
        healthCheckRepository.save(healthCheck);

        String jobName = "health-check-job-" + UUID.randomUUID().toString().substring(0, 8);
        V1Job job = createK8sJobSpec(jobName, healthCheck.getCommand());

        try {
            logger.info("Creating Kubernetes Job '{}' in namespace '{}'", jobName, K8S_NAMESPACE);
            batchV1Api.createNamespacedJob(K8S_NAMESPACE, job).execute();

            waitForJobCompletion(jobName);
            logger.info("Job '{}' completed.", jobName);

            String podName = getPodNameForJob(jobName);
            String podLogs = getPodLogs(podName);

            log.setOutput(podLogs);
            log.setStatus(Status.SUCCESS);

        } catch (ApiException | InterruptedException e) {
            logger.error("Error during Kubernetes Job execution for HealthCheck ID {}: {}", id, e.getMessage());
            log.setOutput("Failed to execute command. Error: " + e.getMessage());
            log.setStatus(Status.FAILED);
            if (e instanceof InterruptedException) {
               Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.error("An unexpected error occurred during health check execution for ID {}: {}", id, e.getMessage());
            log.setOutput("An unexpected error occurred: " + e.getMessage());
            log.setStatus(Status.FAILED);
        } finally {
            log.setEndTime(new Date());
            cleanupJob(jobName);
            healthCheckRepository.save(healthCheck);
        }
        return healthCheck;
    }

    private void validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new InvalidCommandException("Command cannot be empty.");
        }
        String trimmedCommand = command.trim();
        boolean isWhitelisted = COMMAND_WHITELIST.stream().anyMatch(trimmedCommand::startsWith);
        if (!isWhitelisted) {
            throw new InvalidCommandException("Command is not whitelisted. Must start with: " + COMMAND_WHITELIST);
        }
        if (SHELL_METACHARS_PATTERN.matcher(trimmedCommand).find()) {
            throw new InvalidCommandException("Command contains forbidden shell metacharacters.");
        }
    }

    private V1Job createK8sJobSpec(String jobName, String command) {
        V1Container container = new V1Container()
                .name("health-check-container")
                .image("busybox")
                .command(Arrays.asList("/bin/sh", "-c"))
                .args(List.of(command));

        V1PodSpec podSpec = new V1PodSpec()
                .restartPolicy("OnFailure")
                .containers(List.of(container));

        V1PodTemplateSpec podTemplate = new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta().name(jobName))
                .spec(podSpec);

        V1JobSpec jobSpec = new V1JobSpec()
                .template(podTemplate)
                .backoffLimit(1);

        return new V1Job()
                .apiVersion("batch/v1")
                .kind("Job")
                .metadata(new V1ObjectMeta().name(jobName))
                .spec(jobSpec);
    }

    private void waitForJobCompletion(String jobName) throws ApiException, InterruptedException {
        long timeout = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (System.currentTimeMillis() < timeout) {
            V1Job job = batchV1Api.readNamespacedJob(jobName, K8S_NAMESPACE).execute();
            V1JobStatus status = job.getStatus();
            if (status != null) {
                if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                    return; // Success
                }
                if (status.getFailed() != null && status.getFailed() > 0) {
                    throw new ApiException("Job failed to execute.");
                }
            }
            TimeUnit.SECONDS.sleep(5);
        }
        throw new ApiException("Job timed out.");
    }

    private String getPodNameForJob(String jobName) throws ApiException {
        V1PodList podList = coreV1Api.listNamespacedPod(K8S_NAMESPACE)
            .labelSelector("job-name=" + jobName)
            .limit(1)
            .execute();

        if (podList.getItems().isEmpty()) {
            throw new ApiException("Could not find pod for job " + jobName);
        }
        return podList.getItems().get(0).getMetadata().getName();
    }

    private String getPodLogs(String podName) throws ApiException {
        try {
            return coreV1Api.readNamespacedPodLog(podName, K8S_NAMESPACE).execute();
        } catch (ApiException e) {
            logger.error("Failed to retrieve logs for pod {}: {}", podName, e.getResponseBody());
            return "Error retrieving logs: " + e.getMessage();
        }
    }

    private void cleanupJob(String jobName) {
        try {
            logger.info("Cleaning up Job '{}'", jobName);
            V1DeleteOptions deleteOptions = new V1DeleteOptions().propagationPolicy("Background");
            batchV1Api.deleteNamespacedJob(jobName, K8S_NAMESPACE)
                .body(deleteOptions)
                .execute();
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                logger.error("Failed to clean up Job '{}'. Manual cleanup may be required. Error: {}", jobName, e.getResponseBody());
            }
        }
    }
}

