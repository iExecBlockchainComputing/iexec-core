FROM openjdk:11.0.7-jre-slim

COPY build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENTRYPOINT ["java", "-jar", "/iexec-core.jar"]