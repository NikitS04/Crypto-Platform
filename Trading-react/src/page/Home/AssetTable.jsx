import React, { useMemo, useState } from "react";
import {
    Table,
    TableBody,
    TableCaption,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
  } from "@/components/ui/table"
import { Avatar } from "@radix-ui/react-avatar";
import { AvatarImage } from "@/components/ui/avatar";
import { useNavigate } from "react-router-dom";
import { ScrollArea } from "@/components/ui/scroll-area";
import { LiveChange, LivePriceText } from "@/live/LivePrice";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";

// Numeric columns the table can be sorted by, keyed by the CoinGecko field they read
const SORTABLE = {
    total_volume: "VOLUME",
    market_cap: "MARKET CAP",
    price_change_percentage_24h: "24H",
    current_price: "PRICE",
};

// Each header click cycles: highest first -> lowest first -> original order
const NEXT_DIRECTION = { desc: "asc", asc: null };

const SortableHead = ({ field, sort, onSort }) => {
    const active = sort.field === field;
    const Icon = !active ? ArrowUpDown : sort.direction === "desc" ? ArrowDown : ArrowUp;
    return (
        <TableHead>
            <button type="button" onClick={() => onSort(field)}
                className={`flex items-center gap-1 hover:text-foreground ${active ? "text-foreground" : ""}`}>
                {SORTABLE[field]}
                <Icon className={`h-3 w-3 ${active ? "" : "opacity-40"}`} />
            </button>
        </TableHead>
    );
};
  
const AssetTable = ({coin, category, selectedId, onSelect}) => {

    const navigate = useNavigate()
    const [sort, setSort] = useState({ field: null, direction: null })

    const handleSort = (field) => {
        setSort((prev) => {
            const direction = prev.field === field ? NEXT_DIRECTION[prev.direction] : "desc";
            return direction ? { field, direction } : { field: null, direction: null };
        });
    };

    // Sorted by the market data snapshot, not the streamed price, so rows don't jump around every second.
    // Coins missing the value always go last.
    const rows = useMemo(() => {
        if (!sort.field) return coin;
        const sign = sort.direction === "desc" ? -1 : 1;
        return [...coin].sort((a, b) => {
            const x = a[sort.field], y = b[sort.field];
            if (x == null) return y == null ? 0 : 1;
            if (y == null) return -1;
            return sign * (x - y);
        });
    }, [coin, sort]);


    return (
        <Table>
            <ScrollArea className={`${category=="all"?"h-[77.3vh]":"h-[82vh]"}`}>
            <TableHeader>
                <TableRow>
                <TableHead className="w-[100px]">Coin</TableHead>
                <TableHead>SYMBOL</TableHead>
                <SortableHead field="total_volume" sort={sort} onSort={handleSort} />
                <SortableHead field="market_cap" sort={sort} onSort={handleSort} />
                <SortableHead field="price_change_percentage_24h" sort={sort} onSort={handleSort} />
                <SortableHead field="current_price" sort={sort} onSort={handleSort} />
                </TableRow>
            </TableHeader>
            <TableBody>
                {rows.map((item) =>
                <TableRow key={item.id}
                    // Selecting a row shows it in the side panel; without one (small screens) open the coin page
                    onClick={() => onSelect ? onSelect(item) : navigate(`/market/${item.id}`)}
                    className={`cursor-pointer ${selectedId === item.id ? "bg-muted" : ""}`}>
                    <TableCell className="font-medium flex items-center gap-1">
                        <Avatar className="flex items-center justify-center">
                            <AvatarImage 
                            src={item.image}
                            style={{ width: '30px', height: '30px', objectFit: 'cover' }} 
                            />
                        </Avatar>
                        <span>{item.name}</span>
                    </TableCell>
                    <TableCell>{item.symbol.toUpperCase()}</TableCell>
                    <TableCell>{item.total_volume.toLocaleString()}</TableCell>
                    <TableCell>{item.market_cap.toLocaleString()}</TableCell>
                    <TableCell>
                        <LiveChange symbol={item.symbol} fallback={item.price_change_percentage_24h} />
                    </TableCell>
                    <TableCell className="">
                        <LivePriceText symbol={item.symbol} fallback={item.current_price} />
                    </TableCell>
                </TableRow>
                )}
            </TableBody>
            </ScrollArea>
        </Table>

    )
}

export default AssetTable