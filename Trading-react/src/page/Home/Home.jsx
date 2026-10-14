import { Button } from "@/components/ui/button";
import React, { useEffect } from "react";
import AssetTable from "./AssetTable";
import StockChart from "./StockChart";
import { Avatar } from "@radix-ui/react-avatar";
import { AvatarImage } from "@/components/ui/avatar";
import { Cross1Icon, DotIcon } from "@radix-ui/react-icons";
import { MessageCircle } from "lucide-react";
import { Input } from "@/components/ui/input";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { getCoinList, getTop50CoinList } from "@/State/Coin/Action";
import { LiveBadge, LiveChange, LivePriceText } from "@/live/LivePrice";
import {
    Pagination,
    PaginationContent,
    PaginationEllipsis,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious,
  } from "@/components/ui/pagination"
  

const Home = () => {
    const [category, setCategory] = React.useState("all");
    const [page, setPage] = React.useState(1);
    const [pageError, setPageError] = React.useState("");
    const navigate = useNavigate();
    const [inputValue, setInputValue] = React.useState("");
    const [isBotRelease, setIsBotRelease] = React.useState(false);
    const {coin} = useSelector(store=>store);
    // Coin shown in the side panel; Bitcoin until a row is clicked
    const [selected, setSelected] = React.useState(null);
    const panelCoin = selected
        || coin.top50.find((item) => item.id === "bitcoin")
        || coin.coinList.find((item) => item.id === "bitcoin");
    const panelId = panelCoin?.id || "bitcoin";
    const panelSymbol = panelCoin?.symbol || "btc";
    const isLargeScreen = () => window.matchMedia("(min-width: 1024px)").matches;
    const dispatch=useDispatch();

    const handleCategory = (value) => {
        setCategory(value)
    }

    const handleChange = (e) => {
        setInputValue(e.target.value)
    }

    const handleKeyPress = (event) => {
        if (event.key=="Enter"){
            console.log(inputValue)
        }
        setInputValue("")
    }

    useEffect(() =>{
        dispatch(getCoinList(1))},
        [])

    // "All" shows 10 coins a page. The first 5 pages are slices of the top 50 we already have, which
    // saves CoinGecko calls on the free tier; later pages are fetched (and cached in Redis) on demand.
    const PAGE_SIZE = 10;
    const pageFromTop50 = (p) => coin.top50.length >= p * PAGE_SIZE;
    const allCoins = pageFromTop50(page)
        ? coin.top50.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
        : coin.coinList;
    const hasNextPage = allCoins.length === PAGE_SIZE;

    const goToPage = async (event, next) => {
        event.preventDefault();
        if (next < 1 || (next > page && !hasNextPage)) return;
        setPageError("");
        if (!pageFromTop50(next) && !(await dispatch(getCoinList(next)))) {
            // Stay on the current page rather than show a page number with the wrong coins
            setPageError("Market data is busy (free API limit), try again in a minute.");
            return;
        }
        setPage(next);
    }

    useEffect(() =>{
        dispatch(getTop50CoinList())},
        [])

    // Gainers/losers are the top 50 ranked by 24h change; "all" is the paginated full list
    const visibleCoins = React.useMemo(() => {
        if (category === "all") return allCoins;
        const change = (c) => c.price_change_percentage_24h ?? 0;
        if (category === "topGainers") return [...coin.top50].sort((a, b) => change(b) - change(a));
        if (category === "topLosers") return [...coin.top50].sort((a, b) => change(a) - change(b));
        return coin.top50;
    }, [category, allCoins, coin.top50]);

    const handleBotRelease = () => {
        setIsBotRelease(!isBotRelease)
    }

    return (
        <div className="relative">
            
            <div className="lg:flex">
                
                <div className="lg:w-[50%] lg:broder-r">
                    
                    <div className="p-3 flex items-center gap-4">

                        <Button onClick={()=>handleCategory("all")}
                            variant={category=="all" ? "default" : "outline"}
                            className="rouded-full">All
                        </Button>

                        <Button onClick={()=>handleCategory("top50")}
                            variant={category=="top50" ? "default" : "outline"}
                            className="rouded-full">Top 50
                        </Button>

                        <Button onClick={()=>handleCategory("topGainers")}
                            variant={category=="topGainers" ? "default" : "outline"}
                            className="rouded-full">Top Gainers
                        </Button>

                        <Button onClick={()=>handleCategory("topLosers")}
                            variant={category=="topLosers" ? "default" : "outline"}
                            className="rouded-full">Top Losers
                        </Button>

                        <div className="ml-auto"><LiveBadge /></div>

                    </div>
                    <AssetTable coin={visibleCoins} category={category} selectedId={panelId}
                        onSelect={(item) => isLargeScreen() ? setSelected(item) : navigate(`/market/${item.id}`)}/>

                    <div>
                    {category === "all" && pageError && <p className="text-center text-sm text-red-600 pt-2">{pageError}</p>}
                    {category === "all" && <Pagination>
                        <PaginationContent>
                            <PaginationItem>
                            <PaginationPrevious href="#" onClick={(e) => goToPage(e, page - 1)}
                                aria-disabled={page === 1}
                                className={page === 1 ? "pointer-events-none opacity-40" : "cursor-pointer"} />
                            </PaginationItem>
                            {page > 1 && <PaginationItem>
                            <PaginationLink href="#" onClick={(e) => goToPage(e, page - 1)}>{page - 1}</PaginationLink>
                            </PaginationItem>}
                            <PaginationItem>
                            <PaginationLink href="#" isActive onClick={(e) => e.preventDefault()}>{page}</PaginationLink>
                            </PaginationItem>
                            {hasNextPage && <PaginationItem>
                            <PaginationLink href="#" onClick={(e) => goToPage(e, page + 1)}>{page + 1}</PaginationLink>
                            </PaginationItem>}
                            {hasNextPage && <PaginationItem>
                            <PaginationEllipsis />
                            </PaginationItem>}
                            <PaginationItem>
                            <PaginationNext href="#" onClick={(e) => goToPage(e, page + 1)}
                                aria-disabled={!hasNextPage}
                                className={!hasNextPage ? "pointer-events-none opacity-40" : "cursor-pointer"} />
                            </PaginationItem>
                        </PaginationContent>
                    </Pagination>}







                    </div>

                </div>
                <div className="hidden lg:block lg:w-[50%] p-5">
                    <StockChart coinId={panelId}/>
                    <div className="flex gap-5 items-center">
                        <div>
                            <Avatar>
                                <AvatarImage src={panelCoin?.image}
                                style={{ width: "40px", height: "40px" }}/>
                            </Avatar>

                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <p>{panelSymbol.toUpperCase()}</p>
                                <DotIcon className="text-gray-400"/>
                                <p className="text-gray-400">{panelCoin?.name || "Bitcoin"}</p>

                            </div>

                            <div className="flex items-end gap-2">
                                <LivePriceText symbol={panelSymbol} fallback={panelCoin?.current_price} className="text-xl font-bold" />
                                <LiveChange symbol={panelSymbol} fallback={panelCoin?.price_change_percentage_24h} />
                            </div>
                        </div>


                    </div>

                    {panelCoin && <div className="mt-4 grid grid-cols-3 gap-4 text-sm">
                        <div><p className="text-gray-400">Market cap</p><p>${panelCoin.market_cap?.toLocaleString()}</p></div>
                        <div><p className="text-gray-400">24h volume</p><p>${panelCoin.total_volume?.toLocaleString()}</p></div>
                        <div><p className="text-gray-400">Rank</p><p>#{panelCoin.market_cap_rank}</p></div>
                        <div><p className="text-gray-400">24h high</p><p>${panelCoin.high_24h?.toLocaleString()}</p></div>
                        <div><p className="text-gray-400">24h low</p><p>${panelCoin.low_24h?.toLocaleString()}</p></div>
                        <div><p className="text-gray-400">All-time high</p><p>${panelCoin.ath?.toLocaleString()}</p></div>
                    </div>}

                    <div className="mt-6 flex gap-4">
                        <Button size="lg" className="flex-1 py-6 bg-green-600 hover:bg-green-700"
                            onClick={() => navigate(`/market/${panelId}?trade=BUY`)}>Buy {panelSymbol.toUpperCase()}</Button>
                        <Button size="lg" className="flex-1 py-6 bg-red-600 hover:bg-red-700"
                            onClick={() => navigate(`/market/${panelId}?trade=SELL`)}>Sell {panelSymbol.toUpperCase()}</Button>
                    </div>
                    <Button variant="link" className="w-full mt-2" onClick={() => navigate(`/market/${panelId}`)}>
                        View full details
                    </Button>


                </div>

            </div>
            <section className="absolute bottom-5 right-5 
            z-40 flex flex-col justify-end items-end gap-2">

                {isBotRelease && <div className="rounded-md w-[20rem] md:w-[25rem] lg:w[25rem] h-[70vh] bg-gray-300">
                    <div className="flex justify-between items-center 
                    border-b px-6 h-[12%]">
                        <p>Chat Bot</p>
                        <Button onClick={handleBotRelease} variant="ghost" size="icon">
                            <Cross1Icon/>
                        </Button>
                    </div> 

                    <div className="h-[76%] flex flex-col overflow-y-auto 
                    gap-5 px-5 py-2 scroll-container">

                        <div className="self-start pb-5 w-auto">
                            <div className="justify-end self-end px-5 py-2 rounded-md 
                            bg-gray-200 w-auto">
                                <p>Hi! The crypto assistant is coming soon.</p>
                                <p>You will be able to ask about prices, market cap and more.</p>
                            </div>
                        </div>

                            {/* <div key={i} 
                            className={' $ {i%2==0?"self-start":"self-end"} "pb-5 w-auto"'}>
                                <div className="justify-end self-end px-5 py-2 rounded-md 
                                bg-gray-200 w-auto">
                                    <p>Prompt, who are you</p>
                                </div>
                            </div>

                            <div className="self-start pb-5 w-auto">
                                <div className="justify-end self-end px-5 py-2 rounded-md 
                                bg-gray-200 w-auto">
                                    <p>Answer Hi, Raam Arora</p>
                                </div>
                            </div> */}

                        

                    </div>

                    <div className="h-[12%] border-t">
                        <Input className="w-full h-full border-none outline-none" placeholder="write prompt" onChange={handleChange} value={inputValue} onKeyPress={handleKeyPress} />
                    </div>

                </div>}



                <div className="relative w-[10rem] cursor-pointer group">
                    <Button onClick={handleBotRelease} className="w-full h-[3-rem] gap-2 items-center">
                        <MessageCircle 
                        size={30}
                        className="fill-[#1e293b] -rotate-90 stroke-none 
                        group-hover:fill-[#1a1a1a]"/>
                        <span className="text-2x1">Chat Bot</span>
                    </Button>
                </div>
            </section>

            
        </div>
    )
}

export default Home