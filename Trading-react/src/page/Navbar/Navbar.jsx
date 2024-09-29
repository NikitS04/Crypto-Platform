
import { Button } from "@/components/ui/button";
import { Sheet, SheetTrigger, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { Avatar, AvatarImage } from "@radix-ui/react-avatar";
import { DragHandleHorizontalIcon, MagnifyingGlassIcon } from "@radix-ui/react-icons";

import React from "react";
import Sidebar from "./Sidebar";
import { AvatarFallback } from "@/components/ui/avatar";
import { useSelector } from "react-redux";

const Navbar = () => {

    const {auth} = useSelector(store=>store)

    return (
        <div className="px-2 py-3 border-b z-50 bg-background bg-opacity-0 sticky top-0 left-0 right-0 flex justify-between items center">
            <div className="flex items-center gap-3">
            <Sheet>
                <SheetTrigger>
                    <Button variant="ghost" size="icon" className="rounded-full h-11 w-11">
                        <DragHandleHorizontalIcon className="h-7 w-7"/>
                    </Button>
                </SheetTrigger>
                <SheetContent className="w-72 border-r-0 flex flex-col justify-center" side="left">
                    <SheetHeader>
                        <SheetTitle>
                            <div className="text-3x1 flex justify-center items-center gap-1">
                                <Avatar>
                                    <AvatarImage src='https://d1csarkz8obe9u.cloudfront.net/posterpreviews/cryptocurrency-logo-design-template-cd2275b80fff78aafcf5c4c6b74cede7_screen.jpg?ts=1647276957'/>

                                </Avatar>

                                <div>
                                    <span className="font-bold text-orange-700">Nikit</span>
                                    <span className="font-bold">Trading</span>
                                </div>


                            </div>
                        
                        </SheetTitle>
                    </SheetHeader>
                    <Sidebar/>
                </SheetContent>
            </Sheet>
            <p className="text-sm lg:text-base cursor-pointer">
                Nikit Trading
            </p>
        <div className="p-0 ml-9">
            <Button variant="outline" className="flex items-center gap-3">
                <MagnifyingGlassIcon/>
                <span>Search</span>
            </Button>

        </div>
        </div>
        <div>
            <Avatar>
                <AvatarFallback>
                    Hello {auth.user?.fullName.toUpperCase()}
                </AvatarFallback>
            </Avatar>
        </div>
    </div>
    );
};


export default Navbar