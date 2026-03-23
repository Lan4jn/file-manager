package com.poss.file.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatHttpConnectorConfig {

    @Value("${app.security.http-port:-1}")
    private int httpPort;

    @Value("${server.port:8080}")
    private int httpsPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnectorCustomizer() {
        return factory -> {
            if (httpPort <= 0 || httpPort == httpsPort) {
                return;
            }
            factory.addAdditionalTomcatConnectors(createHttpConnector());
        };
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(httpPort);
        return connector;
    }
}
