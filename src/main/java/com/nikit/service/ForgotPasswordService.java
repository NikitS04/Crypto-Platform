package com.nikit.service;

import com.nikit.domain.VerificationType;
import com.nikit.modal.ForgotPasswordToken;
import com.nikit.modal.User;

public interface ForgotPasswordService {

    ForgotPasswordToken createToken(User user,
                                    String id,
                                    String otp,
                                    VerificationType verificationType,
                                    String sendTo);

    ForgotPasswordToken findById(String id);

    ForgotPasswordToken findByUser(Long userId);

    void deleteToken (ForgotPasswordToken token);

}
