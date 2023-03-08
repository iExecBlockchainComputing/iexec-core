FROM eclipse-temurin:11.0.18_10-jre

ARG jar

RUN test -n "$jar"

RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

COPY $jar iexec-core.jar

ENTRYPOINT ["java", "-jar", "/iexec-core.jar"]
