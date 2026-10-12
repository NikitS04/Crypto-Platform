package com.nikit.controller;

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

    // Transactions are only ever created server-side as a side effect of wallet operations,
    // so there is deliberately no public endpoint for creating them.

    @GetMapping
    public ResponseEntity<List<WalletTransaction>> getUserTransactions(@RequestHeader("Authorization") String jwt) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        List<WalletTransaction> transactions = walletTransactionService.getTransactionsByUser(user.getId());
        return new ResponseEntity<>(transactions, HttpStatus.OK);
    }
}
