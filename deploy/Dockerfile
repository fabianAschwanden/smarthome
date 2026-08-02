# Multi-Stage-Build: baut Backend (Quarkus) + Frontend (Angular via Quinoa) im
# Container, sodass auf dem Server nur Docker nötig ist – kein Java/Node/Maven.

# --- Build-Stage: JDK 25 + Node 22 ---
FROM eclipse-temurin:25-jdk AS build
# Node aus dem offiziellen, versionsgepinnten Node-Image kopieren statt
# "curl -fsSL https://deb.nodesource.com/setup_22.x | bash -": jenes Setup-Skript wird
# unverifiziert aus dem Netz geladen und als root ausgefuehrt.
# Die Distro-Pakete (nodejs/npm via apt) reichen NICHT: Debian/Ubuntu liefern 22.22.1,
# Angular verlangt ^22.22.3 -> "npm run build" bricht mit EBADENGINE ab.
COPY --from=node:22.22.3-bookworm /usr/local/bin/node /usr/local/bin/node
COPY --from=node:22.22.3-bookworm /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
  && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
  && apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates \
  && rm -rf /var/lib/apt/lists/* \
  && node --version && npm --version
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

# Unprivilegiert laufen: die App braucht keine Root-Rechte. Wichtig, weil der Container
# mit Host-Netz laeuft und die gitignorte config/ (local-keys) gemountet bekommt.
RUN useradd --system --uid 10001 --no-create-home app && chown -R 10001 /app
USER 10001
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
