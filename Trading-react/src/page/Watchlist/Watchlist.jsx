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
import { Button } from "@/components/ui/button";
import { BookmarkFilledIcon } from "@radix-ui/react-icons";
import { useDispatch, useSelector } from "react-redux";
import { addItemToWatchlist, getUserWatchlist } from "@/State/Watchlist/Action";
import { store } from "@/State/Store";
import { existInWatchlist } from "@/utils/existInWatchlist";

const Watchlist = () =>{

    const dispatch = useDispatch();

    const {watchlist} = useSelector(store=>store)


    const handleRemoveToWatchlist = (value) =>{
        dispatch(addItemToWatchlist({coinId:value, jwt:localStorage.getItem("jwt")}))
    }

    useEffect(()=>{
        dispatch(getUserWatchlist(localStorage.getItem("jwt")))
    }, [])

    const handleAddToWatchlist = (item) =>{
        dispatch(addItemToWatchlist({coinId:item?.id, jwt:localStorage.getItem("jwt")}))
    }

    return(
        <div className="px-5 lg:px-20 pt-5">
        <h1 className="font-bold text-3xl pb-5">Watchlist</h1>
        <Table className="border">
            <TableHeader >
                <TableRow>
                <TableHead className="py-5">COIN</TableHead>
                <TableHead>SYMBOL</TableHead>
                <TableHead>24H MAX</TableHead>
                <TableHead>24H MIN</TableHead>
                <TableHead>RANK</TableHead>
                <TableHead>24H</TableHead>
                <TableHead className="">PRICE</TableHead>
                <TableHead className="text-red-600">REMOVE</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {watchlist.items.map((item, index) =>
                <TableRow key={index}>
                    <TableCell className="font-medium flex items-center gap-2">
                        <Avatar className="-z-50">
                            <AvatarImage 
                            src={item.image}
                            className="w-10 h-10"
                            // style={{ width: "30px", height: "30px" }}
                            />
                        </Avatar>
                        <span>{item.name}</span>
                    </TableCell>
                    <TableCell>{item.symbol.toUpperCase()}</TableCell>
                    <TableCell className="text-green-600">{item.high_24h}</TableCell>
                    <TableCell className="text-red-600">{item.low_24h}</TableCell>
                    <TableCell className="">{item.market_cap_rank}</TableCell>
                    <TableCell><p style={{ color: item.price_change_percentage_24h > 0 ? 'green' : 'red' }}>
                                {item.price_change_percentage_24h.toFixed(2)}%
                                </p>
                    </TableCell>
                    <TableCell className="">${item.current_price.toLocaleString()}</TableCell>
                    <TableCell className="">
                        <Button variant="ghost" 
                        onClick={() => handleRemoveToWatchlist(item.id)} 
                        size="icon" 
                        className="h-10 w-10">
                            <BookmarkFilledIcon className="w-6 h-6"/>
                        </Button>
                    </TableCell>
                </TableRow>
                )}
            </TableBody>
        </Table>

    </div>
    )
}

export default Watchlist