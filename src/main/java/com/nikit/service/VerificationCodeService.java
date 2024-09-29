package com.nikit.service;

import com.nikit.domain.VerificationType;
import com.nikit.modal.User;
import com.nikit.modal.VerificationCode;

public interface VerificationCodeService {
    VerificationCode sendVerificationCode(User user, VerificationType verificationType);

    VerificationCode getVerificationCodeById(Long id) throws Exception;

    VerificationCode getVerifcationCodeByUser(Long userId);


    void deleteVerificationCodeById(VerificationCode verificationCode);
}
