FROM eclipse-temurin:21-jre-jammy@sha256:e9aaf73145bbd1f9f6ec7f6867dd75a44f34b1a6c32a813504bf4129be2d09d7 AS runtime-base
RUN apt-get update && apt-get upgrade --no-install-recommends -y \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 cadence && useradd -u 10001 -g cadence -M -s /usr/sbin/nologin cadence

FROM runtime-base AS application
WORKDIR /app
COPY --chown=10001:10001 target/cadence-0.0.1-SNAPSHOT.jar /app/cadence.jar
COPY docker/application/healthcheck.sh /app/healthcheck.sh
RUN chmod 0555 /app/healthcheck.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-Djdk.httpclient.disableRetryConnect=true", "-Djdk.httpclient.redirects.retrylimit=1", "-Djdk.httpclient.enableAllMethodRetry=false", "-jar", "/app/cadence.jar"]
