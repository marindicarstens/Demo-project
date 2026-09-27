# Multi-stage build: compiles with Maven, then runs on a minimal JRE image, for the
# compact-object-headers JVM flag below.

# --- Build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml eclipse-formatter.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B \
    && mv target/*.jar target/app.jar

# --- Runtime stage ---
FROM eclipse-temurin:25-jre AS runtime

# curl only, for the container healthcheck below - not needed by the app itself.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 1001 --create-home appuser
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
USER appuser

EXPOSE 8080

# 127.0.0.1, not localhost - see frontend/Dockerfile for why (same IPv6-first resolution trap).
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -f http://127.0.0.1:8080/actuator/health || exit 1

# Compact object headers (JEP 519, finalized in Java 25 but still experimental-gated) -
# reduced per-object memory overhead, still behind the unlock flag below.
ENTRYPOINT ["java", \
    "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", \
    "-jar", "app.jar"]
