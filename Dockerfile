FROM eclipse-temurin:21.0.12_8-jre-noble AS extractor

ARG jar

RUN test -n "$jar"

WORKDIR /extractor

COPY $jar iexec-core.jar

RUN java -Djarmode=tools -jar iexec-core.jar extract --layers

FROM eclipse-temurin:21.0.12_8-jre-noble

RUN apt-get update \
    && apt-get upgrade --no-install-recommends -y \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1001 appuser \
    && useradd -g 1001 --no-create-home -s /sbin/nologin -u 1001 appuser

RUN install -d -g 1001 -o 1001 /app

COPY --from=extractor --chown=1001:1001 /extractor/iexec-core/dependencies/ /app
COPY --from=extractor --chown=1001:1001 /extractor/iexec-core/snapshot-dependencies/ /app
COPY --from=extractor --chown=1001:1001 /extractor/iexec-core/application/ /app

USER 1001
WORKDIR /app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "iexec-core.jar"]
