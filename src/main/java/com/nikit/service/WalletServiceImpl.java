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

import java.math.BigDecimal;
import java.time.LocalDate;
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
            walletRepository.save(wallet);

        }
        return wallet;
    }

    @Override
    public Wallet addBalance(Wallet wallet, Long money) {
        BigDecimal balance = wallet.getBalance();
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
        throw new Exception("Wallet not found");
    }

    @Override
    public Wallet walletToWalletTransfer(User sender, Wallet recieverWallet, Long amount) throws Exception {
        Wallet senderWallet = getUserWallet(sender);

        if(senderWallet.getBalance().compareTo(BigDecimal.valueOf(amount))<0){
            throw new Exception("Insufficient Balance");
        }

        BigDecimal senderBalance = senderWallet.getBalance()
                .subtract(BigDecimal.valueOf(amount));
        senderWallet.setBalance(senderBalance);
        walletRepository.save(senderWallet);

        BigDecimal receiverBalance = recieverWallet.getBalance()
                .add(BigDecimal.valueOf(amount));
        recieverWallet.setBalance(receiverBalance);
        walletRepository.save(recieverWallet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(senderWallet);
        transaction.setType(WalletTransactionType.WALLET_TRANSFER); // Or appropriate type
        transaction.setDate(LocalDateTime.now()); // Set current date
        transaction.setTransferId(UUID.randomUUID().toString()); // Generate unique ID
        transaction.setPurpose("Transfer to Wallet ID: " + recieverWallet.getId());
        transaction.setAmount(amount);

        // Save transaction
        walletTransactionRepository.save(transaction);

        return senderWallet;
    }

    @Override
    public Wallet payOrderPayment(Order order, User user) throws Exception {
        Wallet wallet = getUserWallet(user);

        if(order.getOrderType().equals(OrderType.BUY)){
            BigDecimal newBalance = wallet.getBalance().subtract(order.getPrice());
            if(newBalance.compareTo(order.getPrice())<0){
                throw new Exception("Insufficient funds for this transaction");
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
}
