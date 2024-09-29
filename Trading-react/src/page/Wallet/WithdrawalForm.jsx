import { withdrawalRequest } from "@/State/Withdrawal/Action";
import { Button } from "@/components/ui/button";
import { DialogClose } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import React from "react";
import { useDispatch, useSelector } from "react-redux";

const WithdrawalForm = () =>{
    
    const dispatch = useDispatch();
    const {wallet, withdrawal} = useSelector(store=>store)
    const maskedAccountNumber = withdrawal.paymentDetails?.accountNumber
    ? `**** **** **** ${withdrawal.paymentDetails.accountNumber.slice(-4)}`
    : "N/A";

    const[amount, setAmount] = React.useState("")

    const handleChange = (e) => {
        setAmount(e.target.value)
    }

    const handleSubmit = () =>{
        dispatch(withdrawalRequest({amount, jwt:localStorage.getItem("jwt")}))
        console.log(amount)
    }

    return(
        <div className="pt-10 space-y-5">

            <div className="flex justify-between items-center rounded-md
            bg-gray-100 text-xl font-bold px-5 py-4">

                <p>Available balance</p>
                <p>$9000</p>



            </div>

            <div className="flex flex-col items-center">

                <h1>Enter Withdrawal Amount</h1>

                <div className="flex items-center justify-center">

                    <Input
                    onChange={handleChange}
                    value={amount}
                    className="withdrawalInput py-7 border-none outline-none 
                    focus:outline-none px-0 text-2xl text-center"
                    placeholder="$9999"
                    type="number"
                    />

                </div>

            </div>

            <div>
                <p className="pb-2">Transfer To</p>
                    <div className="flex items-center gap-5 border px-5 py-2 rounded-md">
                        <img 
                        className="h-8 w-8"
                        src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMwAAADACAMAAAB/Pny7AAAAY1BMVEX///8AAAC9vb0hISGGhoYuLi6Li4usrKzr6+v6+vqgoKBycnKo
                        qKjw8PB5eXklJSWUlJTi4uLX19fNzc1/f3/Dw8O3t7ccHBxdXV06Ojqampo/Pz8zMzMTExNiYmJJSUlUVFQZYlhPAAACxElEQVR4nO3b65aqIACGYdHGxNLykKUd7/8qd9mMJqnVzi00+31
                        +oqvFJyqEYFkAAAAAAAAAAAAAAAAAAACAbp698nTXYSBesdnlBz/QXY8BSPe4F2fr41LqrsubZJGLH/vc112ddwTFQjR8zT+1dbxiLe4s/A98FchIbZW6daLPah4Z+R1RPi6OjP28O8rF1o8/
                        I45Mlttm1Q/z6dTfNMuORaK7ok9IXCWKWEaX8miiFB9d0+MkoRpFzL5vKLlRj2zTWG9te8Wz3f3zEVVH74/twqjv9zSKZk7Ls+7UJ7QcFY6RceJDW13Pj0Z9ylf7GTPD4siuKA9b5hrHoBd1EM866y
                        lENXxpeWYqaWzGP4QgUV+6ymX/vurB3duswU30x5GrtLeOorOfUe3TRPPNJrOOp/rWociyor9dSjtbb5joiTqeLXqGnbep9b7XVk80zPO2egc4q+cu+ZPy3xSGlhlOcjdIfsdR7yg6eNjLvMLV3G
                        /G6fZrINuJ9hFnkNgDMWA8A+BvRf7yIlMH8nZYWinFQXY52zVz7rkaDBya1QurXqRRXI23dwZOn8m6+8wa5XWf2Dh/WhVPzJkA+OHV8wCNjzBeHea2WPpVcWhe/3IbplHe3jKEGQ1hCDMCwhBmB
                        IQhzAgIQ5gREIYwIyAMYUZAGMKMgDCEGUHQMddstYexsqrYwLlmKzmVuzGEkza/s87z9dpx1uu8mTEKr4vr9xsDvwJYVnz9PjNV75pkfuGr3/i9aXm6r/0TM4B3eEv3bUtDOpu4ZQPT6xwz3mqn
                        IbIIsdGdozRQmJPuHKW+5eYvSHXnKHXvA3iF5mXAlWg6AEOyAP+atN3JQFx1EdfoWR5tJHmJq/c/Z/Jgq+xrNC/S/lXL5+OBxjJXG72djcxOC2cgi5PmnU2WjIdacG7bJm8OBgAAAAAAAAAAAAA
                        AAAAA+G/9AbYCMfPvCcZmAAAAAElFTkSuQmCC"
                        />
                        <div>
                            <p className="text-xl font-bold">{withdrawal.paymentDetails?.bankName} BANK</p>
                            <p className="text-xs">{maskedAccountNumber}</p>
                        </div>
                        
                    </div>



            </div>
            <DialogClose className="w-full">
                <Button onClick={handleSubmit} className="w-full py-7 text-xl">
                    Withdraw
                </Button>
            </DialogClose>
        </div>
    )
}

export default WithdrawalForm