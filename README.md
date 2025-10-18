Kaiburr Assessment - Task 1: Java REST API & MongoDB Backend
This repository contains the complete, runnable source code for Task 1 of the Kaiburr technical assessment. The project is a production-ready, secure Java Spring Boot application that provides a RESTful API for managing "task" objects, with all data persisted in a MongoDB database.
The application is built not just to meet the requirements, but to showcase best practices in modern backend development, focusing on security, maintainability, and a clean architecture.
Key Features & Professional Approach
Robust Security First: Security is the primary concern. The API includes a critical validation layer that:
Prevents Shell Injection: Actively scans for and rejects commands containing shell metacharacters (e.g., ;, |, &&), mitigating a major security vulnerability.
Uses an Allow-list: Only permits commands that are explicitly approved (e.g., ping, nslookup), preventing the execution of arbitrary code.
Enterprise-Grade Architecture: The codebase follows a clean, multi-layered architecture (Controller, Service, Repository) for a clear separation of concerns. This makes the application highly maintainable, scalable, and easy to test.
Centralized Exception Handling: A global exception handler (@ControllerAdvice) provides consistent, clean error responses for a professional API experience, preventing stack traces from being exposed to the client.
Seamless Environment Configuration: The application uses Spring Profiles to manage configurations for different environments (local vs. Kubernetes) without any code changes, a standard practice in professional development.
