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
import { getWithdrawalHistory } from "@/State/Withdrawal/Action";


const Withdrawal = () =>{
    const dispatch = useDispatch();
    const {wallet, withdrawal} = useSelector(store=>store)

    useEffect(() =>{
        dispatch(getWithdrawalHistory(localStorage.getItem("jwt")))
    }, [])

    return(
        <div className="px-5 lg:px-20 pt-5">
        <h1 className="font-bold text-3xl pb-5">Withdrawal</h1>
        <Table className="border">
            <TableHeader >
                <TableRow>
                <TableHead className="py-5">Date & Time</TableHead>
                <TableHead>METHOD</TableHead>
                <TableHead>AMOUNT</TableHead>
                <TableHead className="text-right">STATUS</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {withdrawal.history.map((item, index) =>
                <TableRow key={index}>
                    <TableCell>
                    <p className="text-gray-500">
                        <span>{item.date.split('T')[0]}</span> {/* Date part */}
                        <br/>
                        <span className="ml-1">{item.date.split('T')[1].split('.')[0]}</span> {/* Time part with a small gap */}
                    </p>

                    </TableCell>
                    <TableCell>Bank Account</TableCell>
                    <TableCell>{item.amount}</TableCell>
                    <TableCell className="text-right">
                        <span
                            className={
                            item.withdrawalStatus === "PENDING"
                                ? "bg-amber-100 border border-amber-400 text-amber-700 rounded-md px-2 py-1 inline-block"
                                : item.withdrawalStatus === "FAILED"
                                ? "bg-red-100 border border-red-400 text-red-700 rounded-md px-2 py-1 inline-block"
                                : "bg-green-100 border border-green-400 text-green-700 rounded-md px-2 py-1 inline-block"
                            }
                        >
                            {item.withdrawalStatus}
                        </span>
                    </TableCell>
                </TableRow>
                )}
            </TableBody>
        </Table>

    </div>
    )
}

export default Withdrawal