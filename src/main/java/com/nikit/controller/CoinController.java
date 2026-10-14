package com.nikit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.modal.Coin;
import com.nikit.service.CoinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nikit.live.LivePrice;
import com.nikit.live.LivePriceStore;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/coins")

public class CoinController {
    @Autowired
    private CoinService coinService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LivePriceStore livePriceStore;

    // Latest streamed prices keyed by symbol; the browser uses this for the first render, then /ws/prices
    @GetMapping("/live")
    ResponseEntity<Map<String, LivePrice>> getLivePrices() {
        return ResponseEntity.ok(livePriceStore.getAll());
    }

    @GetMapping
    ResponseEntity<List<Coin>> getCoinList(@RequestParam(defaultValue = "1", name = "page") int page) throws Exception {
        List<Coin> coins = coinService.getCoinList(page);
        return new ResponseEntity<>(coins, HttpStatus.ACCEPTED);

    }

    @GetMapping("/{coinId}/chart")
    ResponseEntity<JsonNode> getMarketChart(
            @PathVariable String coinId,
            @RequestParam("days") int days

    ) throws Exception {

        String res = coinService.getMarketChart(coinId, days);
        JsonNode jsonNode = objectMapper.readTree(res);
        return new ResponseEntity<>(jsonNode, HttpStatus.ACCEPTED);


    }


    @GetMapping("/search")
    ResponseEntity<JsonNode> searchCoin (@RequestParam("q") String keyword) throws Exception {
        String coin = coinService.searchCoin(keyword);
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);

    }




    @GetMapping("/top50")
    ResponseEntity<JsonNode> getTop50CoinByMarketCapRank() throws Exception {
        String coin = coinService.getTop50CoinsByMarketCapRank();
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }

    @GetMapping("/trading")
    ResponseEntity<JsonNode> getTradingCoin() throws Exception {
        String coin = coinService.getTradingCoins();
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }

    @GetMapping("/details/{coinId}")
    ResponseEntity<JsonNode> getCoinDetails(@PathVariable String coinId) throws Exception {
        String coin = coinService.getCoinDetails(coinId);
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }



}

