# Stage 1: Build the application
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the final, lean image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/it-ops-health-check-api-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 8080

# Set the active profile to 'kubernetes' when running in a container
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=kubernetes"]