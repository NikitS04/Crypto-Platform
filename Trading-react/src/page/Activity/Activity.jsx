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
import { getAllOrdersForUser } from "@/State/Order/Action";
import { calculateProfit } from "@/utils/calculateProfit";

const Activity = () =>{

    const dispatch = useDispatch();
    const {order} = useSelector(store=>store)

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

    const calculateProfitLoss = (currentPrice, buyPrice, quantity, orderType) => {
        let profitLoss;
        
        // If the order type is "BUY", compare the current price with the buy price
        if (orderType === "BUY") {
          profitLoss = (currentPrice - buyPrice) * quantity;
        } 
        // If the order type is "SELL", compare the sell price with the buy price
        else if (orderType === "SELL") {
          profitLoss = (buyPrice - currentPrice) * quantity;
        }
    
        const formattedProfitLoss = profitLoss.toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });
    
        // Return formatted profit/loss value with the appropriate sign
        return profitLoss >= 0 ? `+$${formattedProfitLoss}` : `-$${Math.abs(formattedProfitLoss)}`;
      };

    useEffect(() => {
        dispatch(getAllOrdersForUser({jwt:localStorage.getItem("jwt")}))

    }, [])

    return(
        <div className="px-5 lg:px-20 pt-5">
        <h1 className="font-bold text-3xl pb-5">Activity</h1>
        <Table className="border">
            <TableHeader >
                <TableRow>
                <TableHead className="py-5">DATE & TIME</TableHead>
                <TableHead>TRADING PAIR</TableHead>
                <TableHead>BUY PRICE</TableHead>
                <TableHead>SELL PRICE</TableHead>
                <TableHead>ORDER TYPE</TableHead>
                <TableHead>QUANTITY</TableHead>
                <TableHead className="">PROFIT/LOSS</TableHead>
                <TableHead className="text-right">VALUE</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {order.orders.map((item, index) =>
                <TableRow key={index}>
                    <TableCell>
                        <p>{item.timestamp.split("T")[0]} <br />
                            {item.timestamp.split("T")[1].split(".")[0]}
                        </p>
                    </TableCell>
                    <TableCell className="font-medium flex items-center gap-2">
                        <Avatar className="-z-50">
                            <AvatarImage 
                            src={item.orderItem.coin.image}
                            style={{ width: "30px", height: "30px" }}/>
                        </Avatar>
                        <span>{item.orderItem.coin.name}</span>
                    </TableCell>
                    <TableCell className="">${item.orderItem.buyPrice}</TableCell>
                    <TableCell>${item.orderItem.sellPrice}</TableCell>
                    <TableCell>{item.orderType}</TableCell>
                    <TableCell>{item.orderItem.quantity}</TableCell>
                    <TableCell
                        className={`${
                        (item.orderItem.coin.current_price - item.orderItem.buyPrice) * item.orderItem.quantity >= 0
                            ? "text-green-600"
                            : "text-red-600"
                        }`}
                    >
                        {calculateProfitLoss(item.orderItem.coin.current_price, item.orderItem.buyPrice, item.orderItem.quantity, item.orderType)}
                    </TableCell>
                    <TableCell className="text-right">${value(item.orderItem.coin.current_price, item.orderItem.quantity).toLocaleString()}</TableCell>
                </TableRow>
                )}
            </TableBody>
        </Table>

    </div>
    )
}

export default Activity