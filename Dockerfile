# Stage 1: Build — uses Maven image (no mvnw wrapper needed)
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Cache dependencies separately from source code
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the app
COPY src src
RUN mvn package -DskipTests -q

# Stage 2: Runtime — smaller image (JRE only, not JDK)
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
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]

# ---- Optional: GraalVM native binary (build with --target native-runtime) ----
FROM ghcr.io/graalvm/native-image-community:25 AS native-build
WORKDIR /app
COPY --from=build /app/target/ecommerce-api-*.jar app.jar
# AOT-process the jar and compile to native
RUN native-image -jar app.jar \
    --no-fallback \
    -H:Name=ecommerce-api-native \
    -H:+ReportExceptionStackTraces

FROM debian:bookworm-slim AS native-runtime
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=native-build /app/ecommerce-api-native .
USER appuser
EXPOSE 8080
ENTRYPOINT ["/app/ecommerce-api-native"]
