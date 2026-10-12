package com.nikit.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static com.nikit.service.MarketDataClient.*;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * TTL per cache, chosen by how quickly the data actually changes upstream.
     * Prices refresh roughly once a minute on CoinGecko's free tier, so caching them for longer than
     * that would serve stale data, while slow-moving data like search results can be kept much longer.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
                .cacheDefaults(ttl(Duration.ofSeconds(60)))
                .withCacheConfiguration(COIN_MARKETS, ttl(Duration.ofSeconds(60)))
                .withCacheConfiguration(TOP_COINS, ttl(Duration.ofSeconds(60)))
                .withCacheConfiguration(COIN_DETAILS, ttl(Duration.ofSeconds(60)))
                .withCacheConfiguration(MARKET_CHART, ttl(Duration.ofMinutes(5)))
                .withCacheConfiguration(TRENDING, ttl(Duration.ofMinutes(10)))
                .withCacheConfiguration(SEARCH, ttl(Duration.ofHours(1)))
                // Exposes hit/miss counts as the cache.gets metric on /actuator/metrics
                .enableStatistics();
    }

    private static RedisCacheConfiguration ttl(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("trading:")
                // Values are raw JSON strings, so store them as-is rather than Java-serialised
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string()));
    }

    @Bean
    RestTemplate marketDataRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
