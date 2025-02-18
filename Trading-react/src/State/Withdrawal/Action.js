import api from "@/config/api";

import { ADD_PAYMENT_DETAILS_FAILURE, ADD_PAYMENT_DETAILS_REQUEST, ADD_PAYMENT_DETAILS_SUCCESS, GET_PAYMENT_DETAILS_FAILURE, GET_PAYMENT_DETAILS_REQUEST, GET_PAYMENT_DETAILS_SUCCESS, WITHDRAWAL_HISTORY_FAILURE, WITHDRAWAL_HISTORY_REQUEST, WITHDRAWAL_HISTORY_SUCCESS, WITHDRAWAL_PROCEED_FAILURE, WITHDRAWAL_PROCEED_REQUEST, WITHDRAWAL_PROCEED_SUCCESS, WITHDRAWAL_REQUEST, WITHDRAWAL_REQUEST_FAILURE, WITHDRAWAL_REQUEST_REQUEST, WITHDRAWAL_REQUEST_SUCCESS, WITHDRAWAL_SUCCESS } from "./ActionType";
import PaymentDetails from "@/page/Payment Details/PaymentDetails";

export const withdrawalRequest = ({amount, jwt}) => async dispatch =>{
    dispatch({type: WITHDRAWAL_REQUEST})

    try {
        const response = await api.post(`/api/withdrawal/${amount}`, null, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Withdrawal", response.data)
        dispatch({type: WITHDRAWAL_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: WITHDRAWAL_SUCCESS, payload: error.message})
        console.log("Error", error.message)
        
    }
}

export const proceedWithdrawal = ({id, jwt, accept}) => async dispatch =>{
    dispatch({type: WITHDRAWAL_PROCEED_REQUEST})

    try {
        const response = await api.post(`/api/admin/withdrawal/${id}/proceed/${accept}`, null, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Proceed Withdrawal", response.data)
        dispatch({type: WITHDRAWAL_PROCEED_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: WITHDRAWAL_PROCEED_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}


export const getWithdrawalHistory = jwt => async dispatch =>{
    dispatch({type: WITHDRAWAL_HISTORY_REQUEST})

    try {
        const response = await api.get(`/api/withdrawal`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Withdrawal History", response.data)
        dispatch({type: WITHDRAWAL_HISTORY_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: WITHDRAWAL_HISTORY_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}

export const getAllWithdrawalRequest = jwt => async dispatch =>{
    dispatch({type: WITHDRAWAL_REQUEST_REQUEST})

    try {
        const response = await api.post(`/api/admin/withdrawal`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Withdrawal History", response.data)
        dispatch({type: WITHDRAWAL_REQUEST_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: WITHDRAWAL_REQUEST_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}

export const addPaymentDetails = ({paymentDetails, jwt}) => async dispatch =>{
    dispatch({type: ADD_PAYMENT_DETAILS_REQUEST})

    try {
        const response = await api.post(`/api/payment-details`, paymentDetails, {
            headers: { Authorization : `Bearer ${jwt}`,
                    "Content-Type": "application/json"
            }
        });

        console.log("Withdrawals", response.data)
        dispatch({type: ADD_PAYMENT_DETAILS_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: ADD_PAYMENT_DETAILS_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}


export const getPaymentDetails = ({jwt}) => async dispatch =>{
    dispatch({type: GET_PAYMENT_DETAILS_REQUEST})

    try {
        const response = await api.get(`/api/payment-details`, {
            headers: { Authorization : `Bearer ${jwt}`
            }
        });

        console.log("Payment Details", response.data)
        dispatch({type: GET_PAYMENT_DETAILS_SUCCESS, payload: response.data})
        
    } catch (error) {
        dispatch({type: GET_PAYMENT_DETAILS_FAILURE, payload: error.message})
        console.log("Error", error.message)
        
    }
}