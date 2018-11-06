FROM openjdk:8u171-jre-alpine

ADD build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENTRYPOINT ["java","-jar","/iexec-core.jar"]