# Yaoshu Token — Java Maven Multi-stage Build
# 
# Build:
#   docker build -t yaoshu/token:latest .
# Run:
#   docker run -p 9527:9527 yaoshu/token:latest
#
# Note: pom.xml parent is ai.ylyue:yue-library:j17.3.3.1-SNAPSHOT
# Ensure yue-library is available in your local Maven repository or private registry.

# Stage 1: Build JAR with Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml ./
COPY yaoshu-token-server/pom.xml yaoshu-token-server/
# Download dependencies first (cache layer)
RUN mvn dependency:go-offline -q -DskipTests || true

COPY yaoshu-token-server/ yaoshu-token-server/
RUN mvn package -q -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates tzdata wget \
    && rm -rf /var/lib/apt/lists/* \
    && update-ca-certificates

WORKDIR /app
COPY --from=builder /build/yaoshu-token-server/target/yaoshu-token-server-*.jar yaoshu-token-server.jar
COPY LICENSE NOTICE /licenses/

EXPOSE 9527
ENTRYPOINT ["java", "-jar", "yaoshu-token-server.jar", "--spring.profiles.active=prod"]
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q -O - http://localhost:9527/api/status || exit 1
