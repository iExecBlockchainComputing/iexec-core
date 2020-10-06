FROM openjdk:11.0.7-slim

COPY build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENV JAVA_TOOL_OPTIONS -agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n

ENTRYPOINT ["java", "-jar", "/iexec-core.jar"]