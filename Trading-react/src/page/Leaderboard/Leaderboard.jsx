import { useEffect, useState } from "react";
import api from "@/config/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const REFRESH_MS = 30000;
const MEDALS = { 1: "🥇", 2: "🥈", 3: "🥉" };

const Leaderboard = () => {
    const [board, setBoard] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        const load = () =>
            api.get("/api/leaderboard", { headers: { Authorization: `Bearer ${localStorage.getItem("jwt")}` } })
                .then((res) => { setBoard(res.data); setError(null); })
                .catch((e) => setError(e.response?.data?.message || e.message));
        load();
        const timer = setInterval(load, REFRESH_MS);
        return () => clearInterval(timer);
    }, []);

    return (
        <div className="px-5 lg:px-20 py-5 flex flex-col items-center">
            <div className="w-full lg:w-[60%] space-y-4">
                <h1 className="font-bold text-3xl">Leaderboard</h1>
                <p className="text-gray-500">
                    Traders ranked by time-weighted return, so deposits and withdrawals don&apos;t count as performance.
                    {board?.yourRank && <span className="font-semibold text-black"> You are #{board.yourRank}.</span>}
                </p>
                {error && <p className="text-red-600">{error}</p>}
                <Card>
                    <CardHeader><CardTitle>Top Traders</CardTitle></CardHeader>
                    <CardContent>
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead className="w-20">Rank</TableHead>
                                    <TableHead>Trader</TableHead>
                                    <TableHead className="text-right">Return</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {board?.entries.map((row) => (
                                    <TableRow key={row.rank} className={row.you ? "bg-rose-50 font-semibold" : ""}>
                                        <TableCell>{MEDALS[row.rank] ?? `#${row.rank}`}</TableCell>
                                        <TableCell>{row.name}{row.you && " (you)"}</TableCell>
                                        <TableCell className={`text-right ${row.returnPct >= 0 ? "text-green-600" : "text-red-600"}`}>
                                            {row.returnPct >= 0 ? "+" : ""}{row.returnPct.toFixed(2)}%
                                        </TableCell>
                                    </TableRow>
                                ))}
                                {board && board.entries.length === 0 && (
                                    <TableRow>
                                        <TableCell colSpan={3} className="text-center text-gray-500">
                                            No traders yet. Make a trade to join the board.
                                        </TableCell>
                                    </TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
};

export default Leaderboard;
