package com.nikit;

import com.nikit.exception.MarketDataUnavailableException;
import com.nikit.service.MarketDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class MarketDataCacheTest {

    @Autowired private MarketDataClient marketDataClient;
    @Autowired private CacheManager cacheManager;

    // Stands in for CoinGecko so we can count how many requests actually leave the application
    @MockBean private RestTemplate marketDataRestTemplate;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    private void apiReturns(String body) {
        when(marketDataRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    private void verifyApiCalls(int times) {
        verify(marketDataRestTemplate, times(times))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void repeatedReadsForTheSameCoinCallTheApiOnce() {
        apiReturns("{\"id\":\"bitcoin\"}");

        for (int i = 0; i < 100; i++) {
            assertThat(marketDataClient.getCoinDetails("bitcoin")).isEqualTo("{\"id\":\"bitcoin\"}");
        }

        verifyApiCalls(1);
    }

    @Test
    void eachSymbolHasItsOwnCacheKey() {
        apiReturns("{}");

        marketDataClient.getCoinDetails("bitcoin");
        marketDataClient.getCoinDetails("ethereum");
        marketDataClient.getCoinDetails("bitcoin");

        verifyApiCalls(2);
    }

    @Test
    void chartsAreCachedPerCoinAndTimeRange() {
        apiReturns("{\"prices\":[]}");

        marketDataClient.getMarketChart("bitcoin", 1);
        marketDataClient.getMarketChart("bitcoin", 7);
        marketDataClient.getMarketChart("bitcoin", 1);

        verifyApiCalls(2);
    }

    @Test
    void rateLimitErrorsAreNotCachedAndAreRetried() {
        when(marketDataRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null))
                .thenReturn(ResponseEntity.ok("{\"id\":\"bitcoin\"}"));

        assertThatThrownBy(() -> marketDataClient.getCoinDetails("bitcoin"))
                .isInstanceOf(MarketDataUnavailableException.class);

        assertThat(marketDataClient.getCoinDetails("bitcoin")).isEqualTo("{\"id\":\"bitcoin\"}");
        verifyApiCalls(2);
    }
}
