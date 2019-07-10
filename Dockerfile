#openjdk:12-alpine heavy and not supporting jre-only yet, azul-zulu based on openjdk
FROM azul/zulu-openjdk-alpine:11.0.3-jre

ADD build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENTRYPOINT ["java","-jar","/iexec-core.jar"]