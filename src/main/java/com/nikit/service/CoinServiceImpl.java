package com.nikit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.modal.Coin;
import com.nikit.repository.CoinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CoinServiceImpl implements  CoinService{

    @Autowired
    private CoinRepository coinRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // All external calls go through the cached client, so these methods never hit CoinGecko directly
    @Autowired
    private MarketDataClient marketDataClient;

    @Override
    public List<Coin> getCoinList(int page) throws Exception {
        return objectMapper.readValue(marketDataClient.getCoinMarkets(page),
                new TypeReference<List<Coin>>() {});
    }

    @Override
    public String getMarketChart(String coinId, int days) throws Exception {
        return marketDataClient.getMarketChart(coinId, days);
    }

    @Override
    public String getCoinDetails(String coinId) throws Exception {
        String body = marketDataClient.getCoinDetails(coinId);

        // Persist the latest snapshot so orders and watchlists can reference the coin by id
        JsonNode jsonNode = objectMapper.readTree(body);

        Coin coin = new Coin();
        coin.setId(jsonNode.get("id").asText());
        coin.setName(jsonNode.get("name").asText());
        coin.setSymbol(jsonNode.get("symbol").asText());
        coin.setImage(jsonNode.get("image").get("large").asText());

        JsonNode marketData = jsonNode.get("market_data");

        coin.setCurrentPrice(marketData.get("current_price").get("usd").asDouble());
        coin.setMarketCap(marketData.get("market_cap").get("usd").asLong());
        coin.setMarketCapRank(marketData.get("market_cap_rank").asInt());
        coin.setTotalVolume(marketData.get("total_volume").get("usd").asLong());
        coin.setHigh24h(marketData.get("high_24h").get("usd").asDouble());
        coin.setLow24h(marketData.get("low_24h").get("usd").asDouble());
        coin.setPriceChange24h(marketData.get("price_change_24h").asDouble());
        coin.setPriceChangePercentage24h(marketData.get("price_change_percentage_24h").asDouble());
        coin.setMarketCapChange24h(marketData.get("market_cap_change_24h").asLong());
        coin.setMarketCapChangePercentage24h(marketData.get("market_cap_change_percentage_24h").asDouble());
        coin.setTotalSupply(marketData.get("total_supply").asLong());

        coinRepository.save(coin);

        return body;
    }

    @Override
    public Coin findById(String coinId) throws Exception {
        Optional<Coin> optionalCoin = coinRepository.findById(coinId);
        if (optionalCoin.isEmpty()){
            throw new IllegalArgumentException("Coin not found");
        }
        return optionalCoin.get();
    }

    @Override
    public String searchCoin(String keyword) throws Exception {
        return marketDataClient.searchCoins(keyword);
    }

    @Override
    public String getTop50CoinsByMarketCapRank() throws Exception {
        return marketDataClient.getTop50Coins();
    }

    @Override
    public String getTradingCoins() throws Exception {
        return marketDataClient.getTrendingCoins();
    }
}
