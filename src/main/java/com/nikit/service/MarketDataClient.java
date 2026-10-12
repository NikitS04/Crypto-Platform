package com.nikit.service;

import com.nikit.exception.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Thin client for the CoinGecko API, fronted by a cache-aside layer.
 *
 * Every method is @Cacheable: on a hit the response is served from Redis and CoinGecko is never called;
 * on a miss the API is called once and the raw JSON is stored under a per-symbol key with a per-cache TTL
 * (see CacheConfig). sync = true makes concurrent misses for the same key wait for a single upstream call
 * instead of all hitting the API at once, which matters under CoinGecko's tight rate limits.
 */
@Component
public class MarketDataClient {

    public static final String COIN_MARKETS = "coinMarkets";
    public static final String TOP_COINS = "topCoins";
    public static final String COIN_DETAILS = "coinDetails";
    public static final String MARKET_CHART = "marketChart";
    public static final String TRENDING = "trendingCoins";
    public static final String SEARCH = "coinSearch";

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public MarketDataClient(RestTemplate marketDataRestTemplate,
                            @Value("${coingecko.api.key:}") String apiKey) {
        this.restTemplate = marketDataRestTemplate;
        this.apiKey = apiKey;
    }

    @Cacheable(cacheNames = COIN_MARKETS, key = "#page", sync = true)
    public String getCoinMarkets(int page) {
        return get("/coins/markets?vs_currency=usd&per_page=10&page=" + page);
    }

    @Cacheable(cacheNames = TOP_COINS, key = "'top50'", sync = true)
    public String getTop50Coins() {
        return get("/coins/markets?vs_currency=usd&per_page=50&page=1");
    }

    @Cacheable(cacheNames = COIN_DETAILS, key = "#coinId", sync = true)
    public String getCoinDetails(String coinId) {
        return get("/coins/" + encode(coinId));
    }

    @Cacheable(cacheNames = MARKET_CHART, key = "#coinId + ':' + #days", sync = true)
    public String getMarketChart(String coinId, int days) {
        return get("/coins/" + encode(coinId) + "/market_chart?vs_currency=usd&days=" + days);
    }

    @Cacheable(cacheNames = TRENDING, key = "'trending'", sync = true)
    public String getTrendingCoins() {
        return get("/search/trending");
    }

    @Cacheable(cacheNames = SEARCH, key = "#keyword.trim().toLowerCase()", sync = true)
    public String searchCoins(String keyword) {
        return get("/search?query=" + encode(keyword.trim()));
    }

    // Exceptions propagate without being cached, so a rate-limited or failed call is retried on the next request
    private String get(String path) {
        HttpHeaders headers = new HttpHeaders();
        if (!apiKey.isBlank()) {
            headers.set("x-cg-demo-api-key", apiKey);
        }
        try {
            return restTemplate.exchange(BASE_URL + path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                    .getBody();
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new MarketDataUnavailableException("Market data rate limit reached, please try again shortly");
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Coin not found");
        } catch (RestClientException e) {
            throw new MarketDataUnavailableException("Market data provider is unavailable");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
