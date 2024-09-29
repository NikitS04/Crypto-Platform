package com.nikit.controller;

import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.User;
import com.nikit.modal.WalletTransaction;
import com.nikit.service.UserService;
import com.nikit.service.WalletTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class WalletTransactionController {

    @Autowired
    private WalletTransactionService walletTransactionService;

    @Autowired
    private UserService userService;

    // Existing POST endpoint for creating a transaction
    @PostMapping
    public ResponseEntity<WalletTransaction> createTransaction(
            @RequestParam Long walletId,
            @RequestParam WalletTransactionType type,
            @RequestParam Long amount,
            @RequestParam String transferId,
            @RequestParam String purpose) {
        try {
            WalletTransaction transaction = walletTransactionService.createTransaction(walletId, type, amount, transferId, purpose);
            return new ResponseEntity<>(transaction, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    // New GET endpoint for retrieving all wallet transactions for a user
    @GetMapping
    public ResponseEntity<List<WalletTransaction>> getUserTransactions(@RequestHeader("Authorization") String jwt) {
        try {
            // Extract user from JWT (assuming a method for this exists)
            User user = userService.findUserProfileByJwt(jwt);
            List<WalletTransaction> transactions = walletTransactionService.getTransactionsByUser(user.getId());
            return new ResponseEntity<>(transactions, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }
}
