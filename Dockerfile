FROM openjdk:11.0.7-jre-slim

ARG spring_boot_jar

RUN test -n "$spring_boot_jar"

COPY $spring_boot_jar iexec-core.jar

ENTRYPOINT ["java", "-jar", "/iexec-core.jar"]
