package com.bank.collectionhub.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class FineractConfig {

    @Bean
    public WebClient fineractWebClient(@Value("${fineract.base-url}") String baseUrl,
                                      @Value("${fineract.tenant-id}") String tenantId,
                                      @Value("${fineract.username}") String username,
                                      @Value("${fineract.password}") String password,
                                      @Value("${fineract.insecure-ssl:true}") boolean insecureSsl) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(15));

        if (insecureSsl) {
            httpClient = httpClient.secure(spec -> {
                try {
                    spec.sslContext(SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build());
                } catch (SSLException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Basic " + token)
                .defaultHeader("Fineract-Platform-TenantId", tenantId)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public OpenAPI customerBillingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Customer Billing Hub API - BIAN V14.0")
                .version("1.0.0")
                .description("Servicio reactivo integrado con Apache Fineract 1.12.1 para gestión de lotes y órdenes de cobranza."));
    }
}
