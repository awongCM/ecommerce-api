# Stage 1: Build — uses Maven image (no mvnw wrapper needed)
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Cache dependencies separately from source code
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the app
COPY src src
RUN mvn package -DskipTests -q

# ---- Optional: GraalVM native binary (build with --target native-runtime) ----
# Uses Maven -Pnative (Spring Boot AOT + native-maven-plugin), not raw native-image -jar.
# Unvalidated locally — requires GraalVM 25 and a long compile inside Docker.
FROM ghcr.io/graalvm/native-image-community:25 AS native-build

WORKDIR /app

RUN microdnf install -y maven && microdnf clean all

COPY pom.xml .
RUN mvn dependency:go-offline -q || true
COPY src src
RUN mvn -Pnative package -DskipTests -q

FROM debian:bookworm-slim AS native-runtime

WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=native-build /app/target/ecommerce-api ./ecommerce-api-native
USER appuser
EXPOSE 8080
ENTRYPOINT ["/app/ecommerce-api-native"]

# Stage 2: Runtime — smaller image (JRE only, not JDK)
# Last stage = default target for `docker build .`
FROM eclipse-temurin:25-jre AS runtime

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
  "--enable-preview", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]
