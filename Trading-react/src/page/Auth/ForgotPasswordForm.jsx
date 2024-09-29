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

const ForgotPasswordForm = () =>{

    const form = useForm({
        resolver:"",
        defaultValues:{
            email:"",
        }
    })

    const onSubmit = (data) =>{
        console.log(data)
    }


    return(
        <div>
        <h1 className="text-xl font-bold text-center pb-3">Forgot Password</h1>

        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-6">

                <FormField
                    control={form.control}
                    name="email"
                    render={({ field }) => (
                        <FormItem>
                        <FormControl>
                            <Input
                            className="border w-full border-gray-200 p-5"
                            placeholder="Enter Email" {...field} />
                        </FormControl>
                        <FormMessage />
                        </FormItem>
                    )}
                />


            
                <Button className="w-full py-5" type="submit">
                    Submit
                </Button>




            </form>




        </Form>
        
        </div>
    )
}

export default ForgotPasswordForm