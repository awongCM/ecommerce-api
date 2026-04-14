# Stage 1: Build — uses Maven image (no mvnw wrapper needed)
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache dependencies separately from source code
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the app
COPY src src
RUN mvn package -DskipTests -q

# Stage 2: Runtime — smaller image (JRE only, not JDK)
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install curl for health check and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -r appgroup && \
    useradd -r -g appgroup appuser

COPY --from=build --chown=appuser:appgroup /app/target/ecommerce-api-*.jar app.jar

# Health check using Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]
