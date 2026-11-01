package com.anantghadi.pricing.config;

import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Distributed quote cache, enabled only with {@code --spring.profiles.active=redis}.
 * Quotes are stored as JSON (not JDK serialization) so they stay readable and language-neutral.
 */
@Configuration
@Profile("redis")
public class RedisCacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer quotesCacheCustomizer(ObjectMapper objectMapper,
                                                                    CacheProperties cacheProperties) {
        Jackson2JsonRedisSerializer<QuoteResponse> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, QuoteResponse.class);
        RedisCacheConfiguration quotes = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheProperties.getRedis().getTimeToLive())
                .prefixCacheNameWith(cacheProperties.getRedis().getKeyPrefix())
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
        return builder -> builder.withCacheConfiguration(AppConfig.QUOTES_CACHE, quotes);
    }
}
