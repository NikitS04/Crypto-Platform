package com.nikit.controller;


import com.nikit.modal.*;
import com.nikit.service.PaymentService;
import com.nikit.service.UserService;
import com.nikit.service.WalletService;
import com.nikit.service.WalletTransactionService;
import com.nikit.domain.WalletTransactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletTransactionService walletTransactionService;


    @GetMapping("/api/wallet")
    public ResponseEntity<Wallet> getUserWallet(@RequestHeader("Authorization") String jwt) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);

        Wallet wallet = walletService.getUserWallet(user);

        return  new ResponseEntity<>(wallet, HttpStatus.ACCEPTED);

    }

    @PutMapping("/api/wallet/{walletId}/transfer")
    public  ResponseEntity<Wallet> walletToWalletTransfer(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long walletId,
            @RequestBody WalletTransaction req) throws Exception {
        User senderUser = userService.findUserProfileByJwt(jwt);
        Wallet recieverWallet = walletService.findWalletById(walletId);
        Wallet wallet = walletService.walletToWalletTransfer(senderUser,
                recieverWallet,
                req.getAmount());
        return new ResponseEntity<>(wallet, HttpStatus.ACCEPTED);

    }

    @PutMapping("/api/wallet/deposit")
    public ResponseEntity<Wallet> addBalanceToWallet(
            @RequestHeader("Authorization") String jwt,
            @RequestParam(name="order_id") Long orderId,
            @RequestParam( required = false, name="payment_id") String paymentId)
            throws Exception {
        User user = userService.findUserProfileByJwt(jwt);

        Wallet wallet = walletService.getUserWallet(user);

        PaymentOrder order = paymentService.getPaymentOrderById(orderId);

        if (!order.getUser().getId().equals(user.getId())){
            throw new SecurityException("Payment order does not belong to this user");
        }

        boolean status = paymentService.proceedPaymentOrder(order, paymentId);

        if (status){
            wallet = walletService.addBalance(wallet, order.getAmount());
            walletTransactionService.createTransaction(wallet.getId(), WalletTransactionType.ADD_MONEY,
                    order.getAmount(), UUID.randomUUID().toString(), "Wallet top up");
        }

        return new ResponseEntity<>(wallet, HttpStatus.ACCEPTED);

    }

}
