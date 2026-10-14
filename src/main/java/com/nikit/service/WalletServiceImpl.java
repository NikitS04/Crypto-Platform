package com.nikit.service;

import com.nikit.analytics.PortfolioChangedEvent;
import com.nikit.domain.OrderType;
import com.nikit.domain.WalletTransactionType;
import com.nikit.modal.Order;
import com.nikit.modal.PaymentOrder;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.modal.WalletTransaction;
import com.nikit.repository.PaymentOrderRepository;
import com.nikit.repository.WalletRepository;
import com.nikit.repository.WalletTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Concurrency rule: every operation that changes a balance first takes a row lock on the wallet
 * (SELECT ... FOR UPDATE) and does its check-then-update inside the same transaction. Concurrent
 * requests for the same wallet therefore run one after another instead of racing on a stale balance.
 * Transfers lock both wallets in ascending id order so opposite-direction transfers can't deadlock.
 */
@Service
public class WalletServiceImpl implements WalletService{

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentService paymentService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ApplicationEventPublisher events;


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
    @Transactional
    public Wallet lockUserWallet(User user) {
        Optional<Wallet> locked = walletRepository.findByUserIdForUpdate(user.getId());
        if (locked.isPresent()){
            return fresh(locked.get());
        }
        getUserWallet(user);
        return fresh(walletRepository.findByUserIdForUpdate(user.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public Wallet addBalance(Wallet wallet, Long money) {
        Wallet locked = lockById(wallet.getId());
        locked.setBalance(locked.getBalance().add(BigDecimal.valueOf(money)));
        events.publishEvent(new PortfolioChangedEvent(locked.getUser().getId()));
        return walletRepository.save(locked);
    }

    @Override
    @Transactional
    public Wallet withdraw(User user, Long amount) {
        if (amount == null || amount <= 0){
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        Wallet wallet = lockUserWallet(user);
        if (wallet.getBalance().compareTo(BigDecimal.valueOf(amount)) < 0){
            throw new IllegalArgumentException("Insufficient balance");
        }
        wallet.setBalance(wallet.getBalance().subtract(BigDecimal.valueOf(amount)));
        walletRepository.save(wallet);
        recordTransaction(wallet, WalletTransactionType.WITHDRAWAL, "Bank account withdrawal", -amount);
        events.publishEvent(new PortfolioChangedEvent(user.getId()));
        return wallet;
    }

    @Override
    @Transactional
    public Wallet deposit(User user, Long paymentOrderId, String paymentId) throws Exception {
        PaymentOrder order = paymentOrderRepository.findByIdForUpdate(paymentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found"));

        if (!order.getUser().getId().equals(user.getId())){
            throw new SecurityException("Payment order does not belong to this user");
        }

        Wallet wallet = lockUserWallet(user);
        if (paymentService.proceedPaymentOrder(order, paymentId)){
            wallet.setBalance(wallet.getBalance().add(BigDecimal.valueOf(order.getAmount())));
            walletRepository.save(wallet);
            recordTransaction(wallet, WalletTransactionType.ADD_MONEY, "Wallet top up", order.getAmount());
            events.publishEvent(new PortfolioChangedEvent(user.getId()));
        }
        return wallet;
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

        Long senderWalletId = getUserWallet(sender).getId();
        Long receiverWalletId = recieverWallet.getId();

        if (senderWalletId.equals(receiverWalletId)){
            throw new IllegalArgumentException("Cannot transfer to your own wallet");
        }

        // Always lock the lower id first: two opposite transfers then request locks in the same
        // order, so neither can hold one wallet while waiting for the other (no deadlock)
        Wallet first = lockById(Math.min(senderWalletId, receiverWalletId));
        Wallet second = lockById(Math.max(senderWalletId, receiverWalletId));
        Wallet senderWallet = first.getId().equals(senderWalletId) ? first : second;
        Wallet receiver = first.getId().equals(senderWalletId) ? second : first;

        if(senderWallet.getBalance().compareTo(BigDecimal.valueOf(amount))<0){
            throw new IllegalArgumentException("Insufficient Balance");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(BigDecimal.valueOf(amount)));
        receiver.setBalance(receiver.getBalance().add(BigDecimal.valueOf(amount)));
        walletRepository.save(senderWallet);
        walletRepository.save(receiver);

        recordTransaction(senderWallet, WalletTransactionType.WALLET_TRANSFER,
                "Transfer to Wallet ID: " + receiver.getId(), -amount);
        recordTransaction(receiver, WalletTransactionType.WALLET_TRANSFER,
                "Transfer from Wallet ID: " + senderWallet.getId(), amount);
        events.publishEvent(new PortfolioChangedEvent(senderWallet.getUser().getId()));
        events.publishEvent(new PortfolioChangedEvent(receiver.getUser().getId()));

        return senderWallet;
    }

    @Override
    @Transactional
    public Wallet payOrderPayment(Order order, User user) throws Exception {
        Wallet wallet = lockUserWallet(user);

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

    private Wallet lockById(Long walletId){
        return fresh(walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found")));
    }

    /**
     * Re-reads the row after the lock is held. If the wallet was already loaded earlier in the same
     * transaction, the locking query returns that cached instance with its old balance; refreshing
     * guarantees we act on the value as of the moment we got the lock.
     */
    private Wallet fresh(Wallet wallet){
        entityManager.refresh(wallet);
        return withBalance(wallet);
    }

    private static Wallet withBalance(Wallet wallet){
        if (wallet.getBalance() == null){
            wallet.setBalance(BigDecimal.ZERO);
        }
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
