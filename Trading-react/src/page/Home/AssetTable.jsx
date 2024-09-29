import React, { useEffect } from "react";
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
import { useDispatch } from "react-redux";
import { getCoinList } from "@/State/Coin/Action";
import { ScrollArea } from "@/components/ui/scroll-area";
  
const AssetTable = ({coin, category}) => {

    const dispatch=useDispatch()

    const navigate = useNavigate()



    return (
        <Table>
            <ScrollArea className={`${category=="all"?"h-[77.3vh]":"h-[82vh]"}`}>
            <TableHeader>
                <TableRow>
                <TableHead className="w-[100px]">Coin</TableHead>
                <TableHead>SYMBOL</TableHead>
                <TableHead>VOLUME</TableHead>
                <TableHead>MARKET CAP</TableHead>
                <TableHead>24H</TableHead>
                <TableHead className="">PRICE</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {coin.map((item, index) =>
                <TableRow key={item.id}>
                    <TableCell onClick={()=>navigate(`/market/${item.id}`)} className="font-medium flex items-center gap-1">
                        <Avatar className="-z-50 display-flex alignItems-center justifyContent-center">
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
                        <p style={{ color: item.price_change_percentage_24h > 0 ? 'green' : 'red' }}>
                            {item.price_change_percentage_24h.toFixed(1)}%
                        </p>
                    </TableCell>
                    <TableCell className="">${item.current_price.toLocaleString()}</TableCell>
                </TableRow>
                )}
            </TableBody>
            </ScrollArea>
        </Table>

    )
}

export default AssetTable