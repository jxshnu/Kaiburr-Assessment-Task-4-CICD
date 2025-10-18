package com.example.itopshealthcheck.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileReader;
import java.io.IOException;

@Configuration
@Profile("kubernetes")
public class KubernetesClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesClientConfig.class);

    @Bean
    public ApiClient kubernetesApiClient() throws IOException {
        try {
            // Attempt to load in-cluster configuration first
            ApiClient client = ClientBuilder.cluster().build();
            logger.info("Successfully loaded in-cluster Kubernetes configuration.");
            return client;
        } catch (IOException e) {
            logger.warn("Failed to load in-cluster K8s config, falling back to kubeconfig from file system.", e);
            // Fallback to loading from the default kubeconfig file
            String kubeConfigPath = System.getenv("HOME") + "/.kube/config";
            ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
            logger.info("Successfully loaded Kubernetes configuration from file.");
            return client;
        }
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public BatchV1Api batchV1Api(ApiClient apiClient) {
        return new BatchV1Api(apiClient);
    }
}