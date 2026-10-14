import { useSyncExternalStore } from "react";
import { API_BASE_URL } from "@/config/api";

// One WebSocket shared by the whole app. The server sends a snapshot of every price on connect,
// then only the coins that changed (about once a second).
const WS_URL = API_BASE_URL.replace(/^http/, "ws") + "/ws/prices";
const MAX_RETRY_MS = 30000;

let socket = null;
let prices = {};
let status = "connecting";
let retryMs = 1000;
const listeners = new Set();

function emit() {
    listeners.forEach((listener) => listener());
}

function connect() {
    socket = new WebSocket(WS_URL);
    socket.onopen = () => {
        status = "live";
        retryMs = 1000;
        emit();
    };
    socket.onmessage = (event) => {
        const message = JSON.parse(event.data);
        if (message.type !== "prices") return;
        // New object so React sees a change; untouched coins keep the same reference and don't re-render
        const next = { ...prices };
        for (const p of message.prices) next[p.symbol] = p;
        prices = next;
        emit();
    };
    socket.onclose = () => {
        status = "reconnecting";
        emit();
        setTimeout(connect, retryMs);
        retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
    };
}

function subscribe(listener) {
    if (!socket) connect();
    listeners.add(listener);
    return () => listeners.delete(listener);
}

/** Latest streamed price for a coin symbol ("btc"), or undefined until one arrives. */
export function useLivePrice(symbol) {
    return useSyncExternalStore(subscribe, () => (symbol ? prices[symbol.toLowerCase()] : undefined));
}

/** "connecting" | "live" | "reconnecting" */
export function useLiveStatus() {
    return useSyncExternalStore(subscribe, () => status);
}

export function formatUsd(value) {
    if (value == null || Number.isNaN(value)) return "-";
    const digits = Math.abs(value) >= 1 ? 2 : 6;
    return "$" + value.toLocaleString(undefined, { minimumFractionDigits: Math.min(2, digits), maximumFractionDigits: digits });
}
