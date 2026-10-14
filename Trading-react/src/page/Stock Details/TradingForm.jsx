import { getAssetDetails } from "@/State/Asset/Action";
import { payOrder } from "@/State/Order/Action";
import { getUserWallet } from "@/State/Wallet/Action";
import { Avatar, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CheckCircle2, DotIcon } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { formatUsd, useLivePrice } from "@/live/livePrices";
import { LiveChange, LivePriceText } from "@/live/LivePrice";

const TradingForm= ({ initialType = "BUY" }) =>{
    const dispatch = useDispatch();

    const [orderType, setOrderType] = useState(initialType)

    const[amount, setAmount] = useState("")
    const[quantity, setQuantity] = useState(0)
    // "form" -> "review" -> "placing" -> "done"; the review step and the disabled "placing" state stop accidental double trades
    const[step, setStep] = useState("form")
    const[filled, setFilled] = useState(null)
    const {coin, asset, wallet, order} = useSelector(store=>store)
    const live = useLivePrice(coin.coinDetails?.symbol)
    // Orders fill at the live price on the server, so size them with it too
    const price = live?.price ?? coin.coinDetails?.market_data.current_price.usd

    const insufficient = orderType === "BUY"
        ? Number(amount) > Number(wallet.userWallet?.balance || 0)
        : Number(quantity) > Number(asset.assetDetails?.quantity || 0)


    const handleChange = (e) =>{
        const amount = e.target.value;
        setAmount(amount);
        const volume = calculateBuyCost(amount, price)

        setQuantity(volume)
    }

    const calculateBuyCost = (amount, price) => {
        let volume = amount/price

        let decimalPlaces = Math.max(2, price.toString().split(".")[0].length)
        
        return volume.toFixed(decimalPlaces)
        
    }


    useEffect(()=>{
        dispatch(getUserWallet(localStorage.getItem("jwt")))
        dispatch(getAssetDetails({
            coinId:coin.coinDetails?.id, 
            jwt:localStorage.getItem("jwt")}))
    // Re-run once the coin has loaded, e.g. when the form is opened straight from the home page
    },[coin.coinDetails?.id])

    const jwt = () => localStorage.getItem("jwt")
    const symbol = coin.coinDetails?.symbol?.toUpperCase()

    const handleConfirm = async () => {
        if (step === "placing") return;
        setStep("placing")
        const placed = await dispatch(payOrder({
            jwt: jwt(),
            amount,
            orderData: {
                coinId: coin.coinDetails?.id,
                quantity,
                orderType,
            },
        }));

        // Refresh balances either way so the form never shows stale numbers
        await Promise.all([
            dispatch(getUserWallet(jwt())),
            dispatch(getAssetDetails({ coinId: coin.coinDetails?.id, jwt: jwt() })),
        ]);

        if (placed) {
            setFilled(placed)
            setStep("done")
        } else {
            setStep("form")
        }
    };

    const startNewTrade = () => {
        setAmount("")
        setQuantity(0)
        setFilled(null)
        setStep("form")
    }

    if (step === "done" && filled) {
        const unitPrice = filled.orderType === "SELL" ? filled.orderItem?.sellPrice : filled.orderItem?.buyPrice
        return (
            <div className="space-y-6 p-5 text-center">
                <CheckCircle2 className={`mx-auto h-14 w-14 ${filled.orderType === "SELL" ? "text-red-500" : "text-green-500"}`} />
                <div>
                    <p className="text-xl font-semibold">
                        {filled.orderType === "SELL" ? "Sold" : "Bought"} {filled.orderItem?.quantity} {symbol}
                    </p>
                    <p className="text-gray-400">Order #{filled.id} filled</p>
                </div>
                <div className="space-y-2 rounded-md border p-4 text-left">
                    <div className="flex justify-between"><span className="text-gray-400">Fill price</span><span>{formatUsd(unitPrice)}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">Total</span><span>{formatUsd(Number(filled.price))}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">Wallet balance</span><span>{formatUsd(Number(wallet.userWallet?.balance))}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">{symbol} held</span><span>{asset.assetDetails?.quantity || 0}</span></div>
                </div>
                <Button className="w-full py-6" onClick={startNewTrade}>Place another trade</Button>
            </div>
        )
    }

    if (step === "review" || step === "placing") {
        const placing = step === "placing"
        return (
            <div className="space-y-6 p-5">
                <p className="text-center text-xl font-semibold">
                    Confirm {orderType === "BUY" ? "purchase" : "sale"}
                </p>
                <div className="space-y-2 rounded-md border p-4">
                    <div className="flex justify-between"><span className="text-gray-400">Action</span>
                        <span className={orderType === "SELL" ? "text-red-500" : "text-green-500"}>{orderType} {symbol}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">Quantity</span><span>{quantity}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">Price now</span><span>{formatUsd(price)}</span></div>
                    <div className="flex justify-between"><span className="text-gray-400">Estimated total</span><span>{formatUsd(Number(quantity) * price)}</span></div>
                </div>
                <p className="text-center text-sm text-gray-400">Market order: fills at the live price when you confirm.</p>
                <div className="flex gap-3">
                    <Button variant="outline" className="flex-1 py-6" disabled={placing} onClick={() => setStep("form")}>Back</Button>
                    <Button className={`flex-1 py-6 ${orderType === "SELL" ? "bg-red-600" : "bg-green-600"}`}
                        disabled={placing} onClick={handleConfirm}>
                        {placing ? "Placing order..." : `Confirm ${orderType}`}
                    </Button>
                </div>
            </div>
        )
    }

    return(
        <div className="space-y-10 p-5">
            <div>

                <div className="flex gap-4 items-center justify-between">
                    <Input
                    className="py-7 focus:outline-none"
                    placeholder="Enter Amount..."
                    onChange={handleChange}
                    value={amount}
                    type="number"
                    name="amount"
                    />
                    <div>
                        <p className="border text-2xl flex justify-center 
                        items-center w-36 h-14 rounded-md">{quantity}</p>

                    </div>

                </div>
                {insufficient && <h1 className="text-red-600 text-center pt-4">
                    {orderType === "BUY" ? "Insufficient Wallet Funds To Buy" : "Insufficient Quantity To Sell"}</h1>}
                {order.error && <h1 className="text-red-600 text-center pt-4">{order.error}</h1>}
            </div>

            <div className="flex gap-5 items-center">
                    <div>
                        <Avatar>
                            <AvatarImage src={coin.coinDetails?.image.small}/>   
                        </Avatar>

                    </div>

                    <div>
                        <div className="flex items-center gap-2">
                            <p>{coin.coinDetails?.symbol.toUpperCase()}</p>
                            <DotIcon className="text-gray-400"/>
                            <p className="text-gray-400">{coin.coinDetails?.name}</p>
                        </div>

                        <div className="flex items-endd gap-2">
                            <LivePriceText symbol={coin.coinDetails?.symbol} fallback={price} className="text-xl font-bold" />
                            <LiveChange symbol={coin.coinDetails?.symbol}
                                fallback={coin.coinDetails?.market_data.price_change_percentage_24h} />
                        </div>

                    </div>

            
            </div>

            <div className="flex items-center justify-between">
                <p>Order Type</p>
                <p>Market Order</p>
            </div>

            <div className="flex items-center justify-between">
                <p>{orderType=="BUY"?"Available Balance":"Available Quantity"}</p>
                <p>{orderType=="BUY"? formatUsd(Number(wallet.userWallet?.balance || 0)) : (asset.assetDetails?.quantity || 0)}</p>
            </div>

            <div>
                <Button onClick={() => setStep("review")} disabled={insufficient || !(Number(quantity) > 0)}
                className={`w-full py-6 
                ${orderType === "SELL" ? "bg-red-600" : "bg-green-600"}`}>
                    {orderType}
                </Button>

                <Button
                variant="link"
                className="w-full mt-5 text-xl"
                onClick={()=>setOrderType(orderType=="BUY"?"SELL":"BUY")}>
                    {orderType=="BUY"?"or SELL": "or BUY"}
                </Button>

            </div>




        </div>
    )
}

export default TradingForm