package com.nikit.service;

import com.nikit.domain.WithdrawalStatus;
import com.nikit.modal.User;
import com.nikit.modal.Withdrawal;
import com.nikit.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WithdrawalServiceImpl implements WithdrawalService{

    @Autowired
    private WithdrawalRepository withdrawalRepository;
    @Override
    public Withdrawal requestWithdrawal(Long amount, User user) {
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setAmount(amount);
        withdrawal.setUser(user);
        withdrawal.setWithdrawalStatus(WithdrawalStatus.PENDING);

        return withdrawalRepository.save(withdrawal);
    }

    @Override
    public Withdrawal proceedWithdrawal(Long withdrawalId, boolean accept) throws Exception {
        Optional<Withdrawal> withdrawal = withdrawalRepository.findById(withdrawalId);
        if (withdrawal.isEmpty()){
            throw new Exception("Withdrawal not found");
        }
        Withdrawal withdrawal1 = withdrawal.get();
        withdrawal1.setDate(LocalDateTime.now());

        if (accept){
            withdrawal1.setWithdrawalStatus(WithdrawalStatus.SUCCESS);
        }else {
            withdrawal1.setWithdrawalStatus(WithdrawalStatus.PENDING);
        }

        return withdrawalRepository.save(withdrawal1);
    }

    @Override
    public List<Withdrawal> getUsersWithdrawalHistory(User user) {
        return withdrawalRepository.findByUserId(user.getId());
    }

    @Override
    public List<Withdrawal> getAllWithdrawalRequest() {
        return withdrawalRepository.findAll();
    }
}
