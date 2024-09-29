export const calculateProfit = (order) =>{
    if(order && order.orderItem?.buyPrice && order.orderItem?.selPrice){
        return order.orderItem?.sellPrice-order.orderItem?.buyPrice
    }
    return "-"
}