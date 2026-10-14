import { useEffect, useRef, useState } from "react";
import { formatUsd, useLivePrice, useLiveStatus } from "./livePrices";

/** Price that follows the live feed and flashes green/red when it ticks up/down. */
export function LivePriceText({ symbol, fallback, className = "" }) {
    const live = useLivePrice(symbol);
    const price = live?.price ?? fallback;
    const previous = useRef(price);
    const [flash, setFlash] = useState(null);

    useEffect(() => {
        if (previous.current != null && price != null && price !== previous.current) {
            setFlash(price > previous.current ? "flash-up" : "flash-down");
            previous.current = price;
            const timer = setTimeout(() => setFlash(null), 700);
            return () => clearTimeout(timer);
        }
        previous.current = price;
    }, [price]);

    return <span className={`rounded px-1 transition-colors ${flash ?? ""} ${className}`}>{formatUsd(price)}</span>;
}

/** 24h change, live when available. */
export function LiveChange({ symbol, fallback, className = "" }) {
    const live = useLivePrice(symbol);
    const change = live?.changePercent24h ?? fallback;
    if (change == null) return <span className={className}>-</span>;
    return (
        <span className={`${change >= 0 ? "text-green-600" : "text-red-600"} ${className}`}>
            {change >= 0 ? "+" : ""}{change.toFixed(2)}%
        </span>
    );
}

/** Small indicator showing whether the price stream is connected. */
export function LiveBadge() {
    const status = useLiveStatus();
    const live = status === "live";
    return (
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500">
            <span className={`h-2 w-2 rounded-full ${live ? "bg-green-500 animate-pulse" : "bg-gray-400"}`} />
            {live ? "LIVE" : status === "connecting" ? "CONNECTING" : "RECONNECTING"}
        </span>
    );
}
