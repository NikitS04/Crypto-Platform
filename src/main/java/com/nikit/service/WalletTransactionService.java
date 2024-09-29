package com.nikit.service;

import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.WalletTransaction;

import java.util.List;

public interface WalletTransactionService {

    List<WalletTransaction> getTransactionsByUser(Long userId);

    WalletTransaction createTransaction(
            Long walletId,
            WalletTransactionType type,
            Long amount,
            String transferId,
            String purpose
    ) throws Exception;
}
