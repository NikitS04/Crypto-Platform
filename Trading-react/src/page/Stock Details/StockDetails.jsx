import { Avatar, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { BookmarkFilledIcon } from "@radix-ui/react-icons";
import { BookmarkIcon, DotIcon } from "lucide-react";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
  } from "@/components/ui/dialog"
  
import React, { useEffect, useState } from "react";
import TradingForm from "./TradingForm";
import StockChart from "../Home/StockChart";
import { useDispatch, useSelector } from "react-redux";
import { useParams, useSearchParams } from "react-router-dom";
import { fetchCoinDetails } from "@/State/Coin/Action";
import { addItemToWatchlist, getUserWatchlist } from "@/State/Watchlist/Action";
import { existInWatchlist } from "@/utils/existInWatchlist";
import { LiveBadge, LiveChange, LivePriceText } from "@/live/LivePrice";

const StockDetails = () =>{

    const {coin, watchlist} = useSelector(store=>store)

    const dispatch = useDispatch();
    const {id} = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    // Which side the trade dialog opens on; null means closed. ?trade=BUY|SELL (from the home page) opens it directly.
    const [tradeType, setTradeType] = useState(null);

    useEffect(() => {
        const requested = searchParams.get("trade")?.toUpperCase();
        if (requested === "BUY" || requested === "SELL") {
            setTradeType(requested);
            setSearchParams({}, { replace: true });
        }
    }, [searchParams]);

    useEffect(()=>{
        dispatch(fetchCoinDetails({coinId:id, 
            jwt:localStorage.getItem("jwt")}))
        dispatch(getUserWatchlist(localStorage.getItem("jwt")))
        
        },[id])

    const handleAddToWatchlist = () =>{
        dispatch(addItemToWatchlist({coinId:coin.coinDetails?.id, jwt:localStorage.getItem("jwt")}))
    }
    
    return(
        <div className="p-5 mt-5">

            <div className="flex justify-between">
                <div className="flex gap-5 items-center">
                    <div>
                        <Avatar>
                            <AvatarImage 
                                src={
                                    coin.coinDetails?.image.large
                                }  
                            /> 
                        </Avatar>

                    </div>

                    <div>
                        <div className="flex items-center gap-2">
                            <p>{coin.coinDetails?.symbol.toUpperCase()}</p>
                            <DotIcon className="text-gray-400"/>
                            <p className="text-gray-400">{coin.coinDetails?.name}</p>
                            <LiveBadge />
                        </div>

                        <div className="flex items-end gap-2">
                            <LivePriceText symbol={coin.coinDetails?.symbol}
                                fallback={coin.coinDetails?.market_data.current_price.usd}
                                className="text-xl font-bold" />
                            <LiveChange symbol={coin.coinDetails?.symbol}
                                fallback={coin.coinDetails?.market_data.price_change_percentage_24h} />
                        </div>

                    </div>

                </div>

                <div className="flex items-center gap-5">
                    <Button onClick={handleAddToWatchlist}>
                        {existInWatchlist(watchlist.items, coin.coinDetails) ?
                        (<BookmarkFilledIcon className="h-6 w-6"/>)
                        :
                        (<BookmarkIcon className="h-6 w-6"/>)
                        }
                    </Button>

                    <Button size="lg" className="bg-green-600 hover:bg-green-700" onClick={() => setTradeType("BUY")}>Buy</Button>
                    <Button size="lg" className="bg-red-600 hover:bg-red-700" onClick={() => setTradeType("SELL")}>Sell</Button>

                    <Dialog open={tradeType !== null} onOpenChange={(open) => !open && setTradeType(null)}>
                        <DialogContent>
                            <DialogHeader>
                            <DialogTitle>Trade {coin.coinDetails?.symbol?.toUpperCase()}</DialogTitle>
                            </DialogHeader>
                            {/* Keyed by side so switching Buy/Sell from the page starts a fresh form */}
                            <TradingForm key={tradeType} initialType={tradeType || "BUY"}/>
                        </DialogContent>
                    </Dialog>

                </div>

            </div>


            <div className="mt-14">
                <StockChart coinId={id}/>
            </div>

            <div className="mt-6 flex gap-4">
                <Button size="lg" className="flex-1 py-6 bg-green-600 hover:bg-green-700" onClick={() => setTradeType("BUY")}>
                    Buy {coin.coinDetails?.symbol?.toUpperCase()}
                </Button>
                <Button size="lg" className="flex-1 py-6 bg-red-600 hover:bg-red-700" onClick={() => setTradeType("SELL")}>
                    Sell {coin.coinDetails?.symbol?.toUpperCase()}
                </Button>
            </div>


        </div>
    )
}

export default StockDetails