import api from "@/config/api";

import * as types from "./ActionType";

export const getUserWatchlist = (jwt) => async (dispatch) =>{
    dispatch({type: types.GET_USER_WATCHLIST_REQUEST})

    try {
        const response = await api.get(`/api/watchlist/user`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        })

        console.log("User Watchlist", response.data)
        dispatch({type: types.GET_USER_WATCHLIST_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: types.GET_USER_WATCHLIST_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}


export const addItemToWatchlist = ({coinId, jwt}) => async (dispatch) =>{
    dispatch({type: types.ADD_COIN_TO_WATCHLIST_REQUEST})

    try {
        const response = await api.patch(`/api/watchlist/add/coin/${coinId}`, {}, {
            headers: { 
                Authorization : `Bearer ${jwt}`
            }
        })

        console.log("Item Added To Watchlist", response.data)
        dispatch({type: types.ADD_COIN_TO_WATCHLIST_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: types.ADD_COIN_TO_WATCHLIST_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}
