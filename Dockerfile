# syntax=docker/dockerfile:1.12

ARG JDK_IMAGE=eclipse-temurin:21-jdk-noble@sha256:48e318efd142696fe4bcd0637b0f0619daaadcdc2a61b49956a9f90edd15b1f8
ARG JRE_IMAGE=eclipse-temurin:21-jre-noble@sha256:59f873f5bb08175e5d089d3656b9c636f448844a4ef93411581a73a8791e4109

FROM ${JDK_IMAGE} AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY docker/HealthCheck.java ./docker/HealthCheck.java
RUN chmod 0755 gradlew

RUN javac --release 21 \
    -d /workspace/healthcheck \
    docker/HealthCheck.java

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean test bootJar

FROM ${JRE_IMAGE} AS runtime

ARG OCI_SOURCE=https://github.com/BS-Stack-Lab/KTB4-ian-community-BE
ARG OCI_REVISION=local
ARG OCI_VERSION=local

LABEL org.opencontainers.image.source="${OCI_SOURCE}" \
      org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.version="${OCI_VERSION}"

RUN groupadd --gid 10001 community \
    && useradd \
        --uid 10001 \
        --gid community \
        --home-dir /nonexistent \
        --no-create-home \
        --shell /usr/sbin/nologin \
        community \
    && install -d -o community -g community -m 0750 \
        /app /var/lib/community/uploads

COPY --from=builder --chown=community:community \
    /workspace/build/libs/community.jar \
    /app/community.jar
COPY --from=builder --chown=community:community \
    /workspace/healthcheck \
    /app/healthcheck

USER community:community

WORKDIR /app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=10s --start-period=90s --retries=8 \
  CMD ["env", "-u", "JAVA_TOOL_OPTIONS", "java", "-Xms16m", "-Xmx32m", "-cp", "/app/healthcheck", "HealthCheck"]

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/community.jar"]
