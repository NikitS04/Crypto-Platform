package com.nikit.service;

import com.nikit.domain.OrderType;
import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.Order;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.modal.WalletTransaction;
import com.nikit.repository.WalletRepository;
import com.nikit.repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService{

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;


    @Override
    public Wallet getUserWallet(User user) {
        Wallet wallet = walletRepository.findByUserId(user.getId());

        if (wallet==null){
            wallet = new Wallet();
            wallet.setUser(user);
            wallet = walletRepository.save(wallet);

        }
        if (wallet.getBalance()==null){
            wallet.setBalance(BigDecimal.ZERO);
        }
        return wallet;
    }

    @Override
    public Wallet addBalance(Wallet wallet, Long money) {
        BigDecimal balance = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal newBalance = balance.add(BigDecimal.valueOf(money));

        wallet.setBalance(newBalance);

        return walletRepository.save(wallet);
    }

    @Override
    public Wallet findWalletById(Long id) throws Exception {
        Optional<Wallet> wallet = walletRepository.findById(id);

        if (wallet.isPresent()){
            return wallet.get();
        }
        throw new IllegalArgumentException("Wallet not found");
    }

    @Override
    @Transactional
    public Wallet walletToWalletTransfer(User sender, Wallet recieverWallet, Long amount) throws Exception {
        if (amount == null || amount <= 0){
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        Wallet senderWallet = getUserWallet(sender);

        if (senderWallet.getId().equals(recieverWallet.getId())){
            throw new IllegalArgumentException("Cannot transfer to your own wallet");
        }

        if(senderWallet.getBalance().compareTo(BigDecimal.valueOf(amount))<0){
            throw new IllegalArgumentException("Insufficient Balance");
        }

        BigDecimal senderBalance = senderWallet.getBalance()
                .subtract(BigDecimal.valueOf(amount));
        senderWallet.setBalance(senderBalance);
        walletRepository.save(senderWallet);

        BigDecimal receiverBalance = recieverWallet.getBalance()
                .add(BigDecimal.valueOf(amount));
        recieverWallet.setBalance(receiverBalance);
        walletRepository.save(recieverWallet);

        recordTransaction(senderWallet, WalletTransactionType.WALLET_TRANSFER,
                "Transfer to Wallet ID: " + recieverWallet.getId(), -amount);
        recordTransaction(recieverWallet, WalletTransactionType.WALLET_TRANSFER,
                "Transfer from Wallet ID: " + senderWallet.getId(), amount);

        return senderWallet;
    }

    @Override
    @Transactional
    public Wallet payOrderPayment(Order order, User user) throws Exception {
        Wallet wallet = getUserWallet(user);

        if(order.getOrderType().equals(OrderType.BUY)){
            BigDecimal newBalance = wallet.getBalance().subtract(order.getPrice());
            if(newBalance.compareTo(BigDecimal.ZERO)<0){
                throw new IllegalArgumentException("Insufficient funds for this transaction");
            }
            wallet.setBalance(newBalance);
        }
        else{
            BigDecimal newBalance = wallet.getBalance().add(order.getPrice());
            wallet.setBalance(newBalance);
        }
        walletRepository.save(wallet);
        return wallet;
    }

    private void recordTransaction(Wallet wallet, WalletTransactionType type, String purpose, Long amount){
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(type);
        transaction.setDate(LocalDateTime.now());
        transaction.setTransferId(UUID.randomUUID().toString());
        transaction.setPurpose(purpose);
        transaction.setAmount(amount);
        walletTransactionRepository.save(transaction);
    }
}
