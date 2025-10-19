package com.example.itopshealthcheck.service;

import com.example.itopshealthcheck.dto.HealthCheckRequest;
import com.example.itopshealthcheck.exception.InvalidCommandException;
import com.example.itopshealthcheck.model.HealthCheck;
import com.example.itopshealthcheck.repository.HealthCheckRepository;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Use Mockito for testing
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthCheckServiceTest {

    // Create a mock of the repository and other dependencies
    @Mock
    private HealthCheckRepository healthCheckRepository;

    @Mock
    private CoreV1Api coreV1Api;

    @Mock
    private BatchV1Api batchV1Api;

    // Inject the mocks into our service
    @InjectMocks
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        // Before each test, set up the service with the mocks.
        // We pass Optional.empty() because these are not needed for the tests we are writing.
        healthCheckService = new HealthCheckService(healthCheckRepository, Optional.empty(), Optional.empty());
    }

    @Test
    void whenCreateHealthCheckWithValidCommand_thenSuccess() {
        // Arrange: Set up the test data and mock behavior
        HealthCheckRequest request = new HealthCheckRequest();
        request.setName("Valid Ping");
        request.setOwner("Test Owner");
        request.setCommand("ping -c 4 google.com");

        HealthCheck savedHealthCheck = new HealthCheck();
        savedHealthCheck.setId("123");
        savedHealthCheck.setName(request.getName());

        // When the repository's save method is called, return our mock object
        when(healthCheckRepository.save(any(HealthCheck.class))).thenReturn(savedHealthCheck);

        // Act: Call the method we are testing
        HealthCheck result = healthCheckService.createHealthCheck(request);

        // Assert: Check that the result is what we expect
        assertNotNull(result);
        assertEquals("Valid Ping", result.getName());
    }

    @Test
    void whenCreateHealthCheckWithInvalidShellChar_thenThrowException() {
        // Arrange: Set up a request with a malicious command
        HealthCheckRequest request = new HealthCheckRequest();
        request.setCommand("ping google.com; rm -rf /");

        // Act & Assert: Verify that an InvalidCommandException is thrown
        Exception exception = assertThrows(InvalidCommandException.class, () -> {
            healthCheckService.createHealthCheck(request);
        });

        assertTrue(exception.getMessage().contains("forbidden shell metacharacters"));
    }

    @Test
    void whenCreateHealthCheckWithNonWhitelistedCommand_thenThrowException() {
        // Arrange: Set up a request with a non-whitelisted command
        HealthCheckRequest request = new HealthCheckRequest();
        request.setCommand("rm -rf /");

        // Act & Assert: Verify that an InvalidCommandException is thrown
        Exception exception = assertThrows(InvalidCommandException.class, () -> {
            healthCheckService.createHealthCheck(request);
        });

        assertTrue(exception.getMessage().contains("not whitelisted"));
    }
}
```

---

### **Final Step: Commit and Push**

Now that you have reviewed the security hotspot on the SonarCloud website and added the new test file to your project, you just need to commit and push the changes.

```bash
git add .
git commit -m "fix: Add unit tests for service layer to improve code coverage"
git push origin main
