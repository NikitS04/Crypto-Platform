import { getAssetDetails } from "@/State/Asset/Action";
import { payOrder } from "@/State/Order/Action";
import { getUserWallet } from "@/State/Wallet/Action";
import { Avatar, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DotIcon } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

const TradingForm= () =>{
    const dispatch = useDispatch();

    const [orderType, setOrderType] = useState("BUY")

    const[amount, setAmount] = useState(0)
    const[quantity, setQuantity] = useState(0)
    const {coin, asset, wallet} = useSelector(store=>store)


    const handleChange = (e) =>{
        const amount = e.target.value;
        setAmount(amount);
        const volume = calculateBuyCost(amount,
            coin.coinDetails.market_data.current_price.usd
            )

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
    },[])

    const handleBuyCrypto = async () => {
        try {
            // Dispatch the buy order action
            await dispatch(payOrder({
                jwt: localStorage.getItem("jwt"),
                amount,
                orderData: {
                    coinId: coin.coinDetails?.id,
                    quantity,
                    orderType,
                },
            }));

            // After the buy/sell operation, fetch updated wallet and asset details
            await dispatch(getUserWallet(localStorage.getItem("jwt"))); // Fetch updated wallet balance
            await dispatch(getAssetDetails({ coinId: coin.coinDetails?.id, jwt: localStorage.getItem("jwt") })); // Fetch updated asset details

        } catch (error) {
            console.error("Error executing order:", error);
            // Handle error (show notification, etc.)
        }
    };


    return(
        <div className="space-y-10 p-5">
            <div>

                <div className="flex gap-4 items-center justify-between">
                    <Input
                    className="py-7 focus:outline-none"
                    placeholder="Enter Amount..."
                    onChange={handleChange}
                    type="number"
                    name="amount"
                    />
                    <div>
                        <p className="border text-2xl flex justify-center 
                        items-center w-36 h-14 rounded-md">{quantity}</p>

                    </div>

                </div>
                {!true && <h1 className="text-red-600 text-center pt-4">
                    Insufficient Wallet Funds To Buy</h1>}
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
                            <p className="text-xl font-bold">${coin.coinDetails?.market_data.current_price.usd.toLocaleString()}</p>
                            <p className="text-red-600">
                                <span>${coin.coinDetails?.market_data.price_change_24h.toFixed(2)}</span>
                                <span className="ml-2">({coin.coinDetails?.market_data.price_change_percentage_24h.toFixed(2)}%)</span>
                               
                            </p>
                        </div>

                    </div>

            
            </div>

            <div className="flex items-center justify-between">
                <p>Order Type</p>
                <p>Market Order</p>
            </div>

            <div className="flex items-center justify-between">
                <p>{orderType=="BUY"?"Available Balance":"Available Quantity"}</p>
                <p>{orderType=="BUY"? "$" + wallet.userWallet?.balance : (asset.assetDetails?.quantity || 0)}</p>
            </div>

            <div>
                <Button onClick={handleBuyCrypto} 
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