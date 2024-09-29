package com.nikit.service;

import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.Wallet;
import com.nikit.modal.WalletTransaction;
import com.nikit.repository.WalletRepository;
import com.nikit.repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WalletTransactionServiceImpl implements WalletTransactionService {

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Override
    public List<WalletTransaction> getTransactionsByUser(Long userId) {
        return walletTransactionRepository.findByWallet_User_Id(userId);
    }

    @Override
    public WalletTransaction createTransaction(
            Long walletId,
            WalletTransactionType type,
            Long amount,
            String transferId,
            String purpose
    ) throws Exception {

        Optional<Wallet> walletOptional = walletRepository.findById(walletId);
        if (walletOptional.isEmpty()) {
            throw new Exception("Wallet not found for the given ID.");
        }

        Wallet wallet = walletOptional.get();

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setTransferId(transferId);
        transaction.setPurpose(purpose);
        transaction.setDate(LocalDateTime.now());

        return walletTransactionRepository.save(transaction);
    }
}
