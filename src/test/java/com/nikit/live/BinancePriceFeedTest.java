package com.nikit.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BinancePriceFeedTest {

    @Test
    void parsesUsdtPairsFromTheMiniTickerStream() throws Exception {
        // Shape of a real !miniTicker@arr message
        String message = """
                [{"e":"24hrMiniTicker","E":1791822383015,"s":"BTCUSDT","c":"82904.45","o":"83810.01","h":"84352.00","l":"82410.86","v":"12257.9","q":"1023828380.7"},
                 {"e":"24hrMiniTicker","E":1791822383017,"s":"ETHUSDT","c":"2531.85","o":"2537.11","h":"2579.88","l":"2500.00","v":"1","q":"1"},
                 {"e":"24hrMiniTicker","E":1791822383018,"s":"ETHBTC","c":"0.0305","o":"0.0300","h":"0.031","l":"0.03","v":"1","q":"1"},
                 {"e":"24hrMiniTicker","E":1791822383019,"s":"USDT","c":"1","o":"1","h":"1","l":"1","v":"1","q":"1"}]
                """;

        List<LivePrice> prices = BinancePriceFeed.parse(new ObjectMapper().readTree(message));

        assertThat(prices).extracting(LivePrice::symbol).containsExactly("btc", "eth");
        LivePrice btc = prices.get(0);
        assertThat(btc.price()).isEqualTo(82904.45);
        assertThat(btc.changePercent24h()).isCloseTo((82904.45 - 83810.01) / 83810.01 * 100, within(1e-9));
        assertThat(btc.timestamp()).isEqualTo(1791822383015L);
    }
}
