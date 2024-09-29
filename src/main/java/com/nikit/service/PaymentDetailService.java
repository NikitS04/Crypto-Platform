package com.nikit.service;

import com.nikit.modal.PaymentDetails;
import com.nikit.modal.User;

public interface PaymentDetailService {

    public PaymentDetails addPaymentDetails(String accountNumber,
                                            String accountHolderName,
                                            String ifsc,
                                            String bankName,
                                            User user);

    public PaymentDetails getUserPaymentDetails(User user);

}
