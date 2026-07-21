package org.myragent.bootstrap.rag.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * HTTP 客户端配置类
 */
@Configuration
public class HttpClientConfig {

    /**
     * 流式 HTTP 客户端（Primary）
     */
    @Bean
    @Primary
    public OkHttpClient streamingHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ZERO)
                .callTimeout(Duration.ZERO)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 同步 HTTP 客户端
     */
    @Bean
    public OkHttpClient syncHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(45))
                .retryOnConnectionFailure(true)
                .build();
    }
}
