package com.kov.movieinfo.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.kov.movieinfo.dto.MovieResponse;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Typed serializer — we only cache {@link MovieResponse}, so storing the concrete class lets us
     * avoid polymorphic default typing (which otherwise creates a deserialization attack surface
     * for anyone with write access to Redis).
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(
            @Value("${app.cache.ttl}") Duration ttl) {
        var serializer = new Jackson2JsonRedisSerializer<>(MovieResponse.class);

        return builder ->
                builder.cacheDefaults(
                        RedisCacheConfiguration.defaultCacheConfig()
                                .serializeValuesWith(
                                        RedisSerializationContext.SerializationPair.fromSerializer(
                                                serializer))
                                .entryTtl(ttl));
    }
}
