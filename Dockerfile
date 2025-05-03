# --- Stage 1: Build the app ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy Gradle project files
COPY build.gradle* settings.gradle* gradle.properties* ./
COPY gradle ./gradle

# Pre-download dependencies
RUN gradle --no-daemon build || return 0

# Copy source code
COPY src ./src

# Build the application
RUN gradle clean shadowJar --no-daemon

# --- Stage 2: Run the app ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Expose port 5000 for metrics
EXPOSE 5000

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Health check to verify the metrics service is running
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
 CMD curl --fail http://localhost:5000/metrics || exit 1

RUN mkdir -p /app/logs && chmod -R 777 /app/logs

# Copy the built JAR from the build stage
COPY --from=build /app/build/libs/*.jar app.jar
COPY src/main/resources/logback.xml logback.xml

# Run the JAR file
ENTRYPOINT ["java", "-Dlogback.debug=true", "-Dlogging.config=logback.xml", "-jar", "app.jar"]
