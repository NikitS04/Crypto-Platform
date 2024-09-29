import api, { API_BASE_URL } from "@/config/api";
import * as types from "./ActionType"
import axios from "axios";

export const getUserWallet = (jwt)=>async(dispatch)=>{

    dispatch({type: types.GET_USER_WALLET_REQUEST})

    try {
        const resonse = await api.get("/api/wallet", {
            headers:{
                Authorization:`Bearer ${jwt}`,
            }

        });

        console.log("Users Wallet", resonse.data);

        dispatch({type: types.GET_USER_WALLET_SUCCESS, payload : resonse.data})

    } catch (error) {
        dispatch({type: types.GET_USER_WALLET_FAILURE, payload : error.message})
        console.log(error);
        
    }

}

export const getWalletTransactions = ({jwt})=>async(dispatch)=>{

    dispatch({type:types.GET_WALLET_TRANSACTION_REQUEST})

    try {
        const response = await api.get("/api/transactions", 
        {headers:{
            Authorization:`Bearer ${jwt}`,
        }

        });
        dispatch({type:types.GET_WALLET_TRANSACTION_SUCCESS, payload : response.data})
        console.log("Wallet Transactions", response.data);

    } catch (error) {
        dispatch({type:types.GET_WALLET_TRANSACTION_FAILURE, payload : error.message})
        console.log(error);
        
    }

}

export const depositMoney = ({jwt, orderId, paymentId, navigate})=>async(dispatch)=>{

    dispatch({type:types.DEPOSIT_MONEY_REQUEST})
    
    try {
        const response = await api.put("/api/wallet/deposit", null,
            { params:{
                order_id: orderId,
                payment_id:paymentId,
            },
                headers:{
                Authorization: `Bearer ${jwt}`,
            }}

        );
        dispatch({type:types.DEPOSIT_MONEY_SUCCESS, payload : response.data})
        navigate("/wallet")
        console.log("Money deposited", response.data);

    } catch (error) {
        dispatch({type:types.DEPOSIT_MONEY_FAILURE, payload : error.message})
        console.log(error);
        
    }

}


export const paymentHandler = ({jwt, amount, paymentMethod})=>async(dispatch)=>{

    dispatch({type:types.DEPOSIT_MONEY_REQUEST})

    try {
        const response = await api.post(`/api/payment/${paymentMethod}/amount/${amount}`,{}, 
        {headers:{
            Authorization:`Bearer ${jwt}`,
            }
        });

        console.log("Wallet Transactions", response.data);
        window.location.href = response.data.payment_url;
    //  dispatch({type:types.DEPOSIT_MONEY_SUCCESS, payload : response.data})

    } catch (error) {
        dispatch({type:types.DEPOSIT_MONEY_FAILURE, payload : error.message})
        console.log("Wallet Transaction Error", error);
        
    }

}


export const transferMoney = ({jwt, walletId, reqData})=>async(dispatch)=>{

    dispatch({type:types.TRANSFER_MONEY_REQUEST})

    try {
        const response = await api.put(`/api/wallet/${walletId}/transfer`, reqData, 
        {headers:{
            Authorization:`Bearer ${jwt}`,
            "Content-Type": "application/json",
            }
        });

        dispatch({type:types.DEPOSIT_MONEY_SUCCESS, payload : response.data})
        console.log("Wallet Transactions", response.data);

    } catch (error) {
        dispatch({type:types.DEPOSIT_MONEY_FAILURE, payload : error.message})
        console.log(error);
        
    }

}

