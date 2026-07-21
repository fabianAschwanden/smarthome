# Multi-Stage-Build: baut Backend (Quarkus) + Frontend (Angular via Quinoa) im
# Container, sodass auf dem Server nur Docker nötig ist – kein Java/Node/Maven.

# --- Build-Stage: JDK 25 + Node 22 ---
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && apt-get install -y --no-install-recommends nodejs \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY . .
RUN chmod +x mvnw \
  && ./mvnw -B -DskipTests package

# --- Runtime-Stage: schlankes JRE ---
FROM eclipse-temurin:25-jre
WORKDIR /app
# Release-Metadaten (vom Release-Workflow aus dem Git-Tag gesetzt; Default fuer lokale Builds).
ARG VERSION=dev
ARG BUILD_DATE=
ENV APP_VERSION=$VERSION
ENV APP_BUILT_AT=$BUILD_DATE
COPY --from=build /src/target/quarkus-app/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
