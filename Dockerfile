FROM openjdk:11.0.15-jre-slim

ARG jar

RUN test -n "$jar"

COPY $jar iexec-core.jar

ENTRYPOINT ["java", "-jar", "/iexec-core.jar"]
