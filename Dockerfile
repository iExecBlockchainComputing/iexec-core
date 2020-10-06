FROM openjdk:11.0.7-jre-slim

COPY build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENTRYPOINT ["java", "-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n","-jar","/iexec-core.jar"]