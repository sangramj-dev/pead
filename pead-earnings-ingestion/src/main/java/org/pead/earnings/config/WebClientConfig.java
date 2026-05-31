package org.pead.earnings.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${pead.data-sources.polygon.base-url:https://api.polygon.io}")
    private String polygonBaseUrl;

    @Value("${pead.data-sources.fmp.base-url:https://financialmodelingprep.com/api}")
    private String fmpBaseUrl;

    @Primary
    @Bean("polygonWebClient")
    public WebClient polygonWebClient() {
        return WebClient.builder()
                .baseUrl(polygonBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean("fmpWebClient")
    public WebClient fmpWebClient() {
        return WebClient.builder()
                .baseUrl(fmpBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    private HttpClient httpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
    }
}
