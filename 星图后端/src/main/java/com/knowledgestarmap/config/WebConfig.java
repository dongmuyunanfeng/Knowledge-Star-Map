package com.knowledgestarmap.config;

import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> encodingFilterRegistration() {
        log.info("Registering CharacterEncodingFilter with UTF-8, order={}", Ordered.HIGHEST_PRECEDENCE);
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        FilterRegistrationBean<CharacterEncodingFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers((TomcatConnectorCustomizer) connector ->
                connector.setURIEncoding("UTF-8"));
    }
}
