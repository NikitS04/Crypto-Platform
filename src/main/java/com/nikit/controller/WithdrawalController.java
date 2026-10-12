package com.nikit.controller;

import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.modal.Withdrawal;
import com.nikit.service.UserService;
import com.nikit.service.WalletService;
import com.nikit.service.WalletTransactionService;
import com.nikit.service.WithdrawalService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController

public class WithdrawalController {

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private WalletTransactionService walletTransactionService;

    @PostMapping("/api/withdrawal/{amount}")
    @Transactional
    public ResponseEntity<?> withdrawalRequest(
            @PathVariable Long amount,
            @RequestHeader("Authorization") String jwt) throws Exception{
        if (amount == null || amount <= 0){
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        User user = userService.findUserProfileByJwt(jwt);
        Wallet userWallet = walletService.getUserWallet(user);

        if (userWallet.getBalance().compareTo(BigDecimal.valueOf(amount)) < 0){
            throw new IllegalArgumentException("Insufficient balance");
        }

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(amount, user);
        walletService.addBalance(userWallet, -withdrawal.getAmount());

        walletTransactionService.createTransaction(userWallet.getId(), WalletTransactionType.WITHDRAWAL,
                -withdrawal.getAmount(), UUID.randomUUID().toString(), "Bank account withdrawal");

        return new ResponseEntity<>(withdrawal, HttpStatus.OK);
    }

    @PatchMapping("/api/admin/withdrawal/{id}/proceed/{accept}")
    @Transactional
    public ResponseEntity<?> proceedWithdrawal(
            @PathVariable Long id,
            @PathVariable boolean accept) throws Exception{

        Withdrawal withdrawal = withdrawalService.proceedWithdrawal(id, accept);

        if(!accept){
            // Refund the user who requested the withdrawal, not the admin processing it
            Wallet userWallet = walletService.getUserWallet(withdrawal.getUser());
            walletService.addBalance(userWallet, withdrawal.getAmount());
            walletTransactionService.createTransaction(userWallet.getId(), WalletTransactionType.WITHDRAWAL,
                    withdrawal.getAmount(), UUID.randomUUID().toString(), "Withdrawal declined - refund");
        }
        return new ResponseEntity<>(withdrawal, HttpStatus.OK);
    }

    @GetMapping("/api/withdrawal")
    public ResponseEntity<List<Withdrawal>> getWithdrawalHistory(
            @RequestHeader("Authorization") String jwt) throws Exception{

        User user = userService.findUserProfileByJwt(jwt);

        List<Withdrawal> withdrawal = withdrawalService.getUsersWithdrawalHistory(user);

        return new ResponseEntity<>(withdrawal, HttpStatus.OK);
    }

    @GetMapping("/api/admin/withdrawal")
    public ResponseEntity<List<Withdrawal>> getAllWithdrawalHistory() throws Exception{

        List<Withdrawal> withdrawal = withdrawalService.getAllWithdrawalRequest();

        return new ResponseEntity<>(withdrawal, HttpStatus.OK);
    }





}
