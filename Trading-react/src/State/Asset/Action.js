import api from "@/config/api";

import * as types from "./ActionType";

export const getAssetById = ({assetId, jwt}) => async (dispatch) =>{
    dispatch({type: types.GET_ASSET_REQUEST})

    try {
        const response = await api.get(`/api/assets/${assetId}`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Asset By Id", response.data)
        dispatch({type: types.GET_ASSET_SUCCESS, payload: response.data, amount})
        
    } catch (error) {
        dispatch({type: types.GET_ASSET_FAILURE, payload: error.message})
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

export const getAssetDetails = ({coinId, jwt}) => async (dispatch) =>{
    dispatch({type: types.GET_ASSET_DETAILS_REQUEST})

    try {
        const response = await api.get(`/api/asset/coin/${coinId}/user`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Asset Details", response.data)
        dispatch({type: types.GET_ASSET_DETAILS_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: types.GET_ASSET_DETAILS_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}


export const getUserAssets = ({jwt}) => async dispatch =>{
    dispatch({type: types.GET_USER_ASSETS_REQUEST})

    try {
        const response = await api.get(`/api/asset`, {
            headers: { Authorization : `Bearer ${jwt}`
            },
        });

        console.log("My Orders Protfolio", response.data)
        dispatch({type: types.GET_USER_ASSETS_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: types.GET_USER_ASSETS_FAILURE, payload: error.message})
        console.log("Error", error.response?.data)
        console.log("Error", error.message)
        
    }
}

