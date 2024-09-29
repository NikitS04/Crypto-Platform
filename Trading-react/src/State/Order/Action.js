import api from "@/config/api";

import * as types from "./ActionType";

export const payOrder = ({jwt, orderData, amount}) => async (dispatch) =>{
    dispatch({type: types.PAY_ORDER_REQUEST})

    try {
        const response = await api.post(`/api/orders/pay`, orderData, {
            headers: { Authorization : `Bearer ${jwt}`,
                    "Content-Type": "application/json"

            }
        });

        console.log("New Order", response.data)
        dispatch({type: types.PAY_ORDER_SUCCESS, payload: response.data, amount})
        
    } catch (error) {
        dispatch({type: types.PAY_ORDER_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}

// export const getOrderById = ({jwt, orderId}) => async dispatch =>{
//     dispatch({type: types.WITHDRAWAL_PROCEED_REQUEST})

//     try {
//         const response = await api.post(`/api/admin/withdrawal/${id}/proceed/${accept}`, null, {
//             headers: { Authorization : `Bearer ${jwt}`
//             }
//         });

//         console.log("Proceed Withdrawal", response.data)
//         dispatch({type: types.WITHDRAWAL_PROCEED_SUCCESS, payload: response.data})
        
//     } catch (error) {
//         dispatch({type: types.WITHDRAWAL_PROCEED_FAILURE, payload: error.message})
//         console.log("Error", error.message)
        
//     }
// }


export const getAllOrdersForUser = ({jwt, orderType, assetSymbol}) => async dispatch =>{
    dispatch({type: types.GET_ALL_ORDERS_REQUEST})

    try {
        const response = await api.get(`/api/orders`, {
            headers: { Authorization : `Bearer ${jwt}`
            },
            params: {
                order_type:orderType,
                asset_symbol:assetSymbol
            },
        });

        console.log("All Orders", response.data)
        dispatch({type: types.GET_ALL_ORDERS_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: types.GET_ALL_ORDERS_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}

