import { ADD_PAYMENT_DETAILS_SUCCESS, GET_PAYMENT_DETAILS_SUCCESS, WITHDRAWAL_FAILURE, WITHDRAWAL_HISTORY_FAILURE, WITHDRAWAL_HISTORY_REQUEST, WITHDRAWAL_HISTORY_SUCCESS, WITHDRAWAL_PROCEED_FAILURE, WITHDRAWAL_PROCEED_REQUEST, WITHDRAWAL_PROCEED_SUCCESS, WITHDRAWAL_REQUEST, WITHDRAWAL_REQUEST_FAILURE, WITHDRAWAL_REQUEST_REQUEST, WITHDRAWAL_REQUEST_SUCCESS, WITHDRAWAL_SUCCESS } from "./ActionType";

const initialState = {
    withdrawal: null,
    history:[],
    loading: false,
    error: null,
    paymentDetails: null,
    requests: []
};


const withdrawalReducer = (state = initialState, action) => {
    switch (action.type) {
        case WITHDRAWAL_REQUEST:
        case WITHDRAWAL_PROCEED_REQUEST:
        case WITHDRAWAL_REQUEST_REQUEST:
        case WITHDRAWAL_HISTORY_REQUEST: 
            return { 
                ...state,
                loading: true,
                error: null,
        };

        
        case WITHDRAWAL_SUCCESS:
            return {
            ...state,
            withdrawal: action.payload,
            loading: false,
            error: null,
        };

        case ADD_PAYMENT_DETAILS_SUCCESS: 
        case GET_PAYMENT_DETAILS_SUCCESS: 
        return {
            ...state,
            paymentDetails: action.payload,
            loading: false,
            error: null,
        };

        case WITHDRAWAL_PROCEED_SUCCESS:
        return {
            ...state,
            requests: state.requests.map((item) => 
                item.id == action.payload.id ? action.payload : item),
            loading: false,
            error: null,
        };

        case WITHDRAWAL_HISTORY_SUCCESS:
            return{
                ...state,
                history: action.payload,
                loading: false,
                error: action.error,
            };

        case WITHDRAWAL_REQUEST_SUCCESS:
            return{
                ...state,
                requests: action.payload,
                loading: false,
                error: action.error,
            };

        case WITHDRAWAL_FAILURE:
        case WITHDRAWAL_PROCEED_FAILURE:  
        case WITHDRAWAL_HISTORY_FAILURE:
        case WITHDRAWAL_REQUEST_FAILURE:  
            return{
                ...state,
                loading: false,
                error: action.payload,
            };


        default:
            return state;
        }
}

export default withdrawalReducer