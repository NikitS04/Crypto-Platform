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
import { useDispatch, useSelector } from "react-redux";
import { getUserAssets } from "@/State/Asset/Action";
import { store } from "@/State/Store";

const Portfolio = () =>{

    const dispatch = useDispatch();

    const {asset} = useSelector(store=>store)

    console.log("User assets in Portfolio component:", asset.userAssets);

    const value = (currentPrice, quantity) => {
        // Calculate profit/loss
        const profitLoss = (currentPrice) * quantity;
      
        // Format the profit/loss value with 2 decimal places
        const formattedProfitLoss = profitLoss.toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });      
        // Add a "+" sign for positive values and "-" for negative
        return profitLoss;
    };

    const calculateProfitLoss = (currentPrice, buyPrice, quantity) => {
        const profitLoss = (currentPrice - buyPrice) * quantity;
        const formattedProfitLoss = profitLoss.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
        return profitLoss >= 0 ? `+$${formattedProfitLoss}` : `-$${Math.abs(formattedProfitLoss)}`;
    };
      

    useEffect(() => {
        dispatch(getUserAssets({jwt:localStorage.getItem("jwt")}))
    }, [])

    return(
        <div className="px-5 lg:px-20 pt-5">
            <h1 className="font-bold text-3xl pb-5">Portfolio</h1>
            <Table>
                <TableHeader>
                    <TableRow>
                    <TableHead className="">Asset</TableHead>
                    <TableHead>SYMBOL</TableHead>
                    <TableHead>QUANTITY</TableHead>
                    <TableHead>VALUE</TableHead>
                    {/* <TableHead>P/L</TableHead> */}
                    <TableHead>CHANGE % (24H)</TableHead>
                    <TableHead className="text-right">PRICE</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {asset.userAssets.map((item, index) =>
                    <TableRow key={index}>
                        <TableCell className="font-medium flex items-center gap-2">
                            <Avatar className="-z-50">
                                <AvatarImage 
                                src={item.coin.image} className="w-10 h-10"/>
                            </Avatar>
                            <span>{item.coin.name}</span>
                        </TableCell>
                        <TableCell>{item.coin.symbol.toUpperCase()}</TableCell>
                        <TableCell>{item.quantity.toLocaleString()}</TableCell>
                        <TableCell>{value(item.coin.current_price, item.quantity).toLocaleString()}</TableCell>
                        {/* <TableCell style={{
                                color: (item.coin.current_price - item.buyPrice) * item.quantity >= 0 ? 'green' : 'red'
                            }}>
                                {calculateProfitLoss(item.coin.current_price, item.buyPrice, item.quantity)}
                            </TableCell> */}
                        <TableCell  style={{ color: item.coin.price_change_percentage_24h > 0 ? 'green' : (item.coin.price_change_percentage_24h < 0 ? 'red' : 'green') }}
                        >
                            {item.coin.price_change_percentage_24h?.toFixed(2)}%</TableCell>
                        <TableCell className="text-right">${item.coin.current_price.toFixed(2)}</TableCell>
                    </TableRow>
                    )}
                </TableBody>
            </Table>

        </div>
    )
}

export default Portfolio