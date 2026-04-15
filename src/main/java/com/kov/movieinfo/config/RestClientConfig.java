package com.kov.movieinfo.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Built on {@link HttpClient} so we get HTTP/2, automatic connection reuse, and a bounded
     * internal executor — all better suited to the fan-out pattern in our providers than {@code
     * SimpleClientHttpRequestFactory}'s URLConnection-per-call.
     */
    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${app.http.connect-timeout}") Duration connectTimeout,
            @Value("${app.http.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
