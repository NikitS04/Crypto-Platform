package com.nikit.controller;

import com.nikit.domain.PaymentMethod;
import com.nikit.modal.PaymentOrder;
import com.nikit.modal.User;
import com.nikit.response.PaymentResponse;
import com.nikit.service.PaymentService;
import com.nikit.service.UserService;
import com.razorpay.RazorpayException;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {

    @Autowired
    private UserService userService;

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/api/payment/{paymentMethod}/amount/{amount}")
    public ResponseEntity<PaymentResponse> paymentHandler(
            @PathVariable PaymentMethod paymentMethod,
            @PathVariable Long amount,
            @RequestHeader("Authorization") String jwt) throws Exception, RazorpayException, StripeException{

        User user = userService.findUserProfileByJwt(jwt);
        PaymentResponse paymentResponse;

        PaymentOrder order = paymentService.createOrder(user, amount, paymentMethod);

        if (paymentMethod.equals(PaymentMethod.RAZORPAY)){
            paymentResponse = paymentService.createRazorpayPaymentLink(user, amount, order.getId());
        } else {
            paymentResponse = paymentService.createStripePaymentLink(user, amount, order.getId());
        }

        return new ResponseEntity<>(paymentResponse, HttpStatus.CREATED);

    }



}
