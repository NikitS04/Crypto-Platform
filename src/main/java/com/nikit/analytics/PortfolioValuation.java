package com.nikit.analytics;

import com.nikit.live.LivePriceStore;
import com.nikit.modal.Asset;
import com.nikit.modal.Coin;
import org.springframework.stereotype.Component;

/** Prices a holding at the live streamed price when fresh, otherwise the last CoinGecko price. */
@Component
public class PortfolioValuation {

    private static final long LIVE_PRICE_MAX_AGE_MS = 60_000;

    private final LivePriceStore livePriceStore;

    public PortfolioValuation(LivePriceStore livePriceStore) {
        this.livePriceStore = livePriceStore;
    }

    public double priceOf(Coin coin) {
        if (coin.getSymbol() != null) {
            var live = livePriceStore.get(coin.getSymbol()).filter(p -> p.isFresh(LIVE_PRICE_MAX_AGE_MS));
            if (live.isPresent()) {
                return live.get().price();
            }
        }
        return coin.getCurrentPrice();
    }

    public double valueOf(Asset asset) {
        return asset.getQuantity() * priceOf(asset.getCoin());
    }
}
