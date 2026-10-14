package com.nikit.controller;


import com.nikit.modal.*;
import com.nikit.service.UserService;
import com.nikit.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;



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
        Wallet wallet = walletService.deposit(user, orderId, paymentId);
        return new ResponseEntity<>(wallet, HttpStatus.ACCEPTED);

    }

}
