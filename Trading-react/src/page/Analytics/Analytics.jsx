import { useEffect, useState } from "react";
import ReactApexChart from "react-apexcharts";
import api from "@/config/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Avatar, AvatarImage } from "@/components/ui/avatar";
import { formatUsd } from "@/live/livePrices";
import { LiveBadge } from "@/live/LivePrice";

const REFRESH_MS = 10000;

const pct = (v) => (v == null ? "-" : `${v >= 0 ? "+" : ""}${v.toFixed(2)}%`);
// Money amounts always show cents; formatUsd allows more decimals because it's also used for sub-$1 coin prices
const money = (v) => "$" + v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const signedUsd = (v) => (v == null ? "-" : `${v >= 0 ? "+" : "-"}${money(Math.abs(v))}`);
const tone = (v) => (v == null ? "" : v >= 0 ? "text-green-600" : "text-red-600");

const Stat = ({ label, value, className = "", hint }) => (
    <Card>
        <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">{label}</CardTitle>
        </CardHeader>
        <CardContent>
            <p className={`text-2xl font-bold ${className}`}>{value}</p>
            {hint && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
        </CardContent>
    </Card>
);

const Analytics = () => {
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        const load = () =>
            api.get("/api/portfolio/analytics", { headers: { Authorization: `Bearer ${localStorage.getItem("jwt")}` } })
                .then((res) => { setData(res.data); setError(null); })
                .catch((e) => setError(e.response?.data?.message || e.message));
        load();
        // Holdings are valued at live prices server-side, so P&L moves with the market
        const timer = setInterval(load, REFRESH_MS);
        return () => clearInterval(timer);
    }, []);

    if (error) return <p className="p-10 text-red-600">{error}</p>;
    if (!data) return <p className="p-10 text-gray-500">Loading analytics...</p>;

    const needsHistory = `Needs 5+ days of history (${data.daysOfHistory} so far)`;

    const equitySeries = [{ name: "Equity", data: data.equityCurve.map((p) => [p.timestamp, p.equity]) }];
    const equityOptions = {
        chart: { type: "area", height: 320, toolbar: { show: false }, zoom: { enabled: false } },
        dataLabels: { enabled: false },
        stroke: { curve: "stepline", width: 2 },
        xaxis: { type: "datetime" },
        yaxis: { labels: { formatter: (v) => formatUsd(v) } },
        tooltip: {
            x: { format: "dd MMM HH:mm" },
            y: {
                formatter: (v, { dataPointIndex }) =>
                    `${formatUsd(v)} (return ${pct(data.equityCurve[dataPointIndex]?.returnPct)})`,
            },
        },
        fill: { type: "gradient", gradient: { opacityFrom: 0.5, opacityTo: 0.05 } },
        colors: ["#e11d48"],
    };

    const allocationLabels = [...data.holdings.map((h) => h.symbol.toUpperCase()), "Cash"];
    const allocationValues = [...data.holdings.map((h) => h.value), data.cash];
    const allocationOptions = {
        labels: allocationLabels,
        legend: { position: "bottom" },
        dataLabels: { formatter: (v) => `${v.toFixed(1)}%` },
        tooltip: { y: { formatter: (v) => formatUsd(v) } },
    };

    return (
        <div className="px-5 lg:px-20 py-5 space-y-6">
            <div className="flex items-center gap-4">
                <h1 className="font-bold text-3xl">Analytics</h1>
                <LiveBadge />
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
                <Stat label="Total Equity" value={money(data.equity)}
                    hint={`${money(data.cash)} cash + ${money(data.holdingsValue)} holdings`} />
                <Stat label="Total Return (time-weighted)" value={pct(data.totalReturnPct)} className={tone(data.totalReturnPct)}
                    hint="Excludes deposits and withdrawals" />
                <Stat label="Max Drawdown" value={data.maxDrawdownPct ? `-${data.maxDrawdownPct.toFixed(2)}%` : "0.00%"}
                    className={data.maxDrawdownPct ? "text-red-600" : ""} hint="Largest peak-to-trough fall" />
                <Stat label="Unrealised P&L" value={signedUsd(data.unrealizedPnl)} className={tone(data.unrealizedPnl)} />
                <Stat label="Realised P&L" value={signedUsd(data.realizedPnl)} className={tone(data.realizedPnl)} />
                <Stat label="Sharpe Ratio (annualised)"
                    value={data.sharpeRatio == null ? "-" : data.sharpeRatio.toFixed(2)}
                    hint={data.sharpeRatio == null ? needsHistory
                        : `Volatility ${data.volatilityPct.toFixed(1)}% a year, risk-free rate 0`} />
            </div>

            <div className="grid lg:grid-cols-3 gap-4">
                <Card className="lg:col-span-2">
                    <CardHeader><CardTitle>Equity Curve</CardTitle></CardHeader>
                    <CardContent>
                        {data.equityCurve.length > 1
                            ? <ReactApexChart type="area" height={320} series={equitySeries} options={equityOptions} />
                            : <p className="text-gray-500 py-20 text-center">Make a trade to start your equity curve</p>}
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader><CardTitle>Allocation</CardTitle></CardHeader>
                    <CardContent>
                        {data.equity > 0
                            ? <ReactApexChart type="donut" height={320} series={allocationValues} options={allocationOptions} />
                            : <p className="text-gray-500 py-20 text-center">No funds yet</p>}
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader><CardTitle>Holdings</CardTitle></CardHeader>
                <CardContent>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Asset</TableHead>
                                <TableHead>Quantity</TableHead>
                                <TableHead>Avg Cost</TableHead>
                                <TableHead>Price</TableHead>
                                <TableHead>Value</TableHead>
                                <TableHead>Unrealised P&L</TableHead>
                                <TableHead className="text-right">Allocation</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {data.holdings.map((h) => (
                                <TableRow key={h.coinId}>
                                    <TableCell className="flex items-center gap-2 font-medium">
                                        <Avatar className="h-7 w-7"><AvatarImage src={h.image} /></Avatar>
                                        {h.name} <span className="text-gray-400">{h.symbol.toUpperCase()}</span>
                                    </TableCell>
                                    <TableCell>{h.quantity.toLocaleString(undefined, { maximumFractionDigits: 6 })}</TableCell>
                                    <TableCell>{formatUsd(h.avgCost)}</TableCell>
                                    <TableCell>{formatUsd(h.price)}</TableCell>
                                    <TableCell>{money(h.value)}</TableCell>
                                    <TableCell className={tone(h.unrealizedPnl)}>
                                        {signedUsd(h.unrealizedPnl)} ({pct(h.unrealizedPnlPct)})
                                    </TableCell>
                                    <TableCell className="text-right">{h.allocationPct.toFixed(1)}%</TableCell>
                                </TableRow>
                            ))}
                            {data.holdings.length === 0 && (
                                <TableRow><TableCell colSpan={7} className="text-center text-gray-500">No holdings yet</TableCell></TableRow>
                            )}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>
        </div>
    );
};

export default Analytics;
