import { addPaymentDetails } from "@/State/Withdrawal/Action";
import { Button } from "@/components/ui/button"
import { DialogClose } from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import React from "react";
import { useForm } from "react-hook-form";
import { useDispatch } from "react-redux";

const PaymentDetailsForm = () =>{

    const dispatch = useDispatch()

    const form = useForm({
        resolver:"",
        defaultValues:{
            accountHolderName:"",
            ifsc:"",
            accountNumber:"",
            bankName:""
        }
    })

    const onSubmit = (data) =>{
        dispatch(addPaymentDetails({
                paymentDetails:data,
                jwt:localStorage.getItem("jwt")
            })
        )
        console.log(data)
    }


    return(
        <div className="px-10 py-2">
        
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-6">
                <FormField
                control={form.control}
                name="accountHolderName"
                render={({ field }) => (
                    <FormItem>
                    <FormLabel>Account Holder Name</FormLabel>
                    <FormControl>
                        <Input
                        name="accountHolderName"
                        className="border w-full border-gray-200 p-5"
                        placeholder="Nikit" {...field} />
                    </FormControl>
                    <FormMessage />
                    </FormItem>
                )}
                />

                <FormField
                    control={form.control}
                    name="ifsc"
                    render={({ field }) => (
                        <FormItem>
                        <FormLabel>IFSC CODE</FormLabel>
                        <FormControl>
                            <Input
                            className="border w-full border-gray-200 p-5"
                            placeholder="NATWEST123456" {...field} />
                        </FormControl>
                        <FormMessage />
                        </FormItem>
                    )}
                />

                <FormField
                    control={form.control}
                    name="accountNumber"
                    render={({ field }) => (
                        <FormItem>
                        <FormLabel>Account Number</FormLabel>
                        <FormControl>
                            <Input
                            className="border w-full border-gray-200 p-5"
                            placeholder="******5604" {...field} />
                        </FormControl>
                        <FormMessage />
                        </FormItem>
                    )}
                />

                <FormField
                    control={form.control}
                    name="confirmAccountNumber"
                    render={({ field }) => (
                        <FormItem>
                        <FormLabel>Confirm Account Number</FormLabel>
                        <FormControl>
                            <Input
                            className="border w-full border-gray-200 p-5"
                            placeholder="******5604" {...field} />
                        </FormControl>
                        <FormMessage />
                        </FormItem>
                    )}
                />

                <FormField
                    control={form.control}
                    name="bankName"
                    render={({ field }) => (
                        <FormItem>
                        <FormLabel>Bank Name</FormLabel>
                        <FormControl>
                            <Input
                            className="border w-full border-gray-200 p-5"
                            placeholder="NatWest" {...field} />
                        </FormControl>
                        <FormMessage />
                        </FormItem>
                    )}
                />

                <DialogClose className="w-full">
                    <Button className="w-full py-5" type="submit">
                        Submit
                    </Button>
                </DialogClose>




            </form>




        </Form>
        
        </div>
    )
}

export default PaymentDetailsForm