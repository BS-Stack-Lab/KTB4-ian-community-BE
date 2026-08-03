# syntax=docker/dockerfile:1.12

ARG JDK_IMAGE=eclipse-temurin:21-jdk-noble@sha256:35685c7e23352983a48882d97cd9875f5284c228db71d1e2476e5e6c1bab1080
ARG JRE_IMAGE=eclipse-temurin:21-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64

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
