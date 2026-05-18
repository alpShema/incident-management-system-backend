##############################################################################
# STAGE 1: BUILD
# We use maven:3.9.9-eclipse-temurin-21-alpine as the base image because:
# - The project requires Java 21 (as specified in pom.xml <java.version>)
# - Maven 3.9.9 is the recommended build tool version
# - "eclipse-temurin" is the community-maintained OpenJDK distribution
# - "alpine" is a lightweight Linux distro (~5MB) keeping the image small
##############################################################################
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

# Set the working directory inside the container
# All subsequent commands will run from /app
WORKDIR /app

# Copy only pom.xml first (before the rest of the source code)
# This is a Docker caching optimisation:
# If pom.xml hasn't changed, Docker reuses the cached Maven dependency layer
# and skips re-downloading dependencies on every build
COPY pom.xml ./

# Download all declared dependencies into the local Maven cache
# This layer is invalidated only when pom.xml changes
RUN mvn dependency:go-offline -q

# Now copy the entire source tree into the container
# We do this AFTER dependency:go-offline to maximise layer cache hits:
# source code changes won't invalidate the downloaded-dependencies layer
COPY src ./src

# Build the Spring Boot executable ("fat") JAR, skipping tests
# Tests are run as a dedicated pipeline stage before this image is built
# Output: target/hilfe-0.0.1-SNAPSHOT.jar
RUN mvn package -DskipTests

##############################################################################
# STAGE 2: RUN
# We start fresh with a minimal JRE image — no Maven, no source code,
# no build tools. This is a multi-stage build.
# Final image contains only: JRE + the compiled JAR
# Result: small, secure, production-ready image
##############################################################################
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user and group to run the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set the working directory inside the container
WORKDIR /app

# Copy only the built JAR from the builder stage
# A wildcard is used so the version number in the filename doesn't matter
COPY --from=builder /app/target/*.jar app.jar

# Transfer ownership of the JAR to the non-root user
RUN chown appuser:appgroup app.jar

# Drop root — all subsequent commands and the running container use appuser
USER appuser

# Tell Docker this container listens on port 8080 (default Spring Boot port)
# This is documentation — it doesn't actually publish the port
# Publish the port when running: docker run -p 8080:8080
EXPOSE 8080

# Health check: Docker will call the Spring Boot Actuator health endpoint every 30s
# start-period is 30s to allow the JVM and Flyway migrations to finish startup
# If it fails 3 times in a row the container is marked "unhealthy"
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Start the Spring Boot application
# exec form (JSON array) ensures the JVM receives OS signals directly
# so SIGTERM triggers a graceful shutdown instead of being swallowed by a shell
ENTRYPOINT ["java", "-jar", "app.jar"]
