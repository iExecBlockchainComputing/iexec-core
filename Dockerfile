FROM openjdk:8u171-jre-alpine

# Default certificate will only be valid at 'https://localhost:[...]' (and not at 'https://core:[...]' for e.g.)
COPY build/resources/main/ssl-keystore-dev.p12 /ssl/ssl-keystore.p12
ENV IEXEC_CORE_SSL_KEYSTORE /ssl/ssl-keystore.p12

ADD build/libs/iexec-core-@projectversion@.jar iexec-core.jar

ENTRYPOINT ["java","-jar","/iexec-core.jar"]