package com.nikit.service;

import com.nikit.modal.Order;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;

public interface WalletService {

    Wallet getUserWallet (User user);

    /** Returns the user's wallet with a row lock held until the current transaction ends. */
    Wallet lockUserWallet(User user);

    Wallet addBalance(Wallet wallet, Long money);

    Wallet withdraw(User user, Long amount);

    Wallet deposit(User user, Long paymentOrderId, String paymentId) throws Exception;

    Wallet findWalletById(Long id) throws Exception;

    Wallet walletToWalletTransfer(User sender, Wallet recieverWallet, Long amount) throws Exception;

    Wallet payOrderPayment(Order order, User user) throws Exception;
}
