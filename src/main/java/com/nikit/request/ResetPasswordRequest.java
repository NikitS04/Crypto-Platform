package com.nikit.request;

import lombok.Data;

@Data
public class ResetPasswordRequest {

    private String otp;

    private String password;

}
