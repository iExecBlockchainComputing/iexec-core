package com.iexec.core;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpWebServerConfiguration {

    @Value("${server.http.enabled}")
    private boolean isHttpEnabled;

    @Value("${server.http.port}")
    private int httpPort;

    /*
    * This method will allow http connections (without SSL) if set in configuration
    * */
    @Bean
    public ServletWebServerFactory servletContainer() {
        if (isHttpEnabled) {
            return getHttpWebServerFactory();
        }
        return getStandardWebServerFactory();
    }

    private TomcatServletWebServerFactory getHttpWebServerFactory() {
        TomcatServletWebServerFactory factory = getStandardWebServerFactory();
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(httpPort);
        factory.addAdditionalTomcatConnectors(connector);
        return factory;
    }

    private TomcatServletWebServerFactory getStandardWebServerFactory() {
        return new TomcatServletWebServerFactory();
    }

}