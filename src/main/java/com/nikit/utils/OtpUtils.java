package com.nikit.utils;

import java.security.SecureRandom;

public class OtpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateOTP(){
        int otpLength = 6;
        StringBuilder otp = new StringBuilder(otpLength);

        for(int i=0; i<otpLength; i++){
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();

    }
}
