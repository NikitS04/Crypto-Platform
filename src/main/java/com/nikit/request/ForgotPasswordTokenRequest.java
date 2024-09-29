package com.nikit.request;

import com.nikit.domain.VerificationType;
import com.nikit.modal.VerificationCode;
import lombok.Data;

@Data

public class ForgotPasswordTokenRequest {
    private String sendTo;
    private VerificationType verificationType;

}
