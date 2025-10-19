package com.example.itopshealthcheck.service;

import com.example.itopshealthcheck.dto.HealthCheckRequest;
import com.example.itopshealthcheck.exception.InvalidCommandException;
import com.example.itopshealthcheck.exception.ResourceNotFoundException;
import com.example.itopshealthcheck.model.ExecutionLog;
import com.example.itopshealthcheck.model.HealthCheck;
import com.example.itopshealthcheck.model.Status;
import com.example.itopshealthcheck.repository.HealthCheckRepository;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);
    private static final List<String> COMMAND_WHITELIST = Arrays.asList("ping", "nslookup", "wget", "echo");
    private static final Pattern SHELL_METACHARS_PATTERN = Pattern.compile("[;|`&><]");

    private final HealthCheckRepository healthCheckRepository;

    // Using Optional @Autowired for constructor injection.
    // This fixes the "field injection" code smell flagged by SonarCloud.
    private final Optional<CoreV1Api> coreV1Api;
    private final Optional<BatchV1Api> batchV1Api;

    @Autowired
    public HealthCheckService(HealthCheckRepository healthCheckRepository, Optional<CoreV1Api> coreV1Api, Optional<BatchV1Api> batchV1Api) {
        this.healthCheckRepository = healthCheckRepository;
        this.coreV1Api = coreV1Api;
        this.batchV1Api = batchV1Api;
    }

    public List<HealthCheck> getAllHealthChecks() {
        return healthCheckRepository.findAll();
    }

    public HealthCheck getHealthCheckById(String id) {
        return healthCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("HealthCheck not found with id: " + id));
    }

    // This method now accepts the DTO and converts it to a HealthCheck entity.
    public HealthCheck createHealthCheck(HealthCheckRequest request) {
        validateCommand(request.getCommand());
        HealthCheck healthCheck = new HealthCheck();
        healthCheck.setName(request.getName());
        healthCheck.setOwner(request.getOwner());
        healthCheck.setCommand(request.getCommand());
        return healthCheckRepository.save(healthCheck);
    }

    public void deleteHealthCheck(String id) {
        if (!healthCheckRepository.existsById(id)) {
            throw new ResourceNotFoundException("HealthCheck not found with id: " + id);
        }
        healthCheckRepository.deleteById(id);
    }

    public HealthCheck runHealthCheck(String id, String triggeredBy) {
        // Check which profile is active to determine which run logic to use
        if (coreV1Api.isPresent() && batchV1Api.isPresent()) {
            return runHealthCheckKubernetes(id, triggeredBy);
        } else {
            return runHealthCheckLocal(id, triggeredBy);
        }
    }

    @Profile("kubernetes")
    private HealthCheck runHealthCheckKubernetes(String id, String triggeredBy) {
        CoreV1Api actualCoreV1Api = coreV1Api.get();
        BatchV1Api actualBatchV1Api = batchV1Api.get();

        HealthCheck healthCheck = getHealthCheckById(id);
        ExecutionLog log = new ExecutionLog(triggeredBy);
        log.setStatus(Status.RUNNING);
        healthCheck.getExecutionLogs().add(log);
        healthCheckRepository.save(healthCheck);

        String jobName = "health-check-job-" + UUID.randomUUID().toString().substring(0, 8);
        V1Job job = createK8sJobSpec(jobName, healthCheck.getCommand());
        String podName = null;

        try {
            logger.info("Creating Kubernetes Job '{}' in namespace 'health-checks'", jobName);
            actualBatchV1Api.createNamespacedJob("health-checks", job).execute();

            waitForJobCompletion(actualBatchV1Api, jobName);
            logger.info("Job '{}' completed.", jobName);

            podName = getPodNameForJob(actualCoreV1Api, jobName);
            String podLogs = getPodLogs(actualCoreV1Api, podName);

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
            cleanupJob(actualBatchV1Api, jobName);
            healthCheckRepository.save(healthCheck);
        }
        return healthCheck;
    }

    @Profile("local")
    private HealthCheck runHealthCheckLocal(String id, String triggeredBy) {
        HealthCheck healthCheck = getHealthCheckById(id);
        ExecutionLog log = new ExecutionLog(triggeredBy);

        logger.info("LOCAL DEMO: Simulating run for command '{}'", healthCheck.getCommand());
        log.setStatus(Status.SUCCESS);
        log.setOutput("Simulated execution for local demo as Kubernetes client is not available.");
        log.setEndTime(new Date());

        healthCheck.getExecutionLogs().add(log);
        return healthCheckRepository.save(healthCheck);
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

    private void waitForJobCompletion(BatchV1Api api, String jobName) throws ApiException, InterruptedException {
        long timeout = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2);
        while (System.currentTimeMillis() < timeout) {
            V1Job jobStatus = api.readNamespacedJobStatus("health-checks", jobName).execute();
            if (jobStatus != null && jobStatus.getStatus() != null) {
                if (jobStatus.getStatus().getSucceeded() != null && jobStatus.getStatus().getSucceeded() > 0) {
                    return;
                }
                if (jobStatus.getStatus().getFailed() != null && jobStatus.getStatus().getFailed() > 0) {
                    throw new ApiException("Job failed to execute.");
                }
            }
            TimeUnit.SECONDS.sleep(5);
        }
        throw new ApiException("Job timed out.");
    }

    private String getPodNameForJob(CoreV1Api api, String jobName) throws ApiException {
        var podList = api.listNamespacedPod("health-checks")
            .labelSelector("job-name=" + jobName)
            .limit(1)
            .execute();

        if (podList.getItems().isEmpty()) {
            throw new ApiException("Could not find pod for job " + jobName);
        }
        return podList.getItems().get(0).getMetadata().getName();
    }

    private String getPodLogs(CoreV1Api api, String podName) throws ApiException {
        try {
            return api.readNamespacedPodLog("health-checks", podName).execute();
        } catch (ApiException e) {
            logger.error("Failed to retrieve logs for pod {}: {}", podName, e.getResponseBody());
            return "Error retrieving logs: " + e.getMessage();
        }
    }

    private void cleanupJob(BatchV1Api api, String jobName) {
        try {
            logger.info("Cleaning up Job '{}'", jobName);
            api.deleteNamespacedJob("health-checks", jobName).propagationPolicy("Background").execute();
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                logger.error("Failed to clean up Job '{}'. Manual cleanup may be required. Error: {}", jobName, e.getResponseBody());
            }
        }
    }
}
