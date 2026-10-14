package com.nikit.repository;

import com.nikit.modal.WalletTransaction;
import com.nikit.domain.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWallet_User_Id(Long userId);

    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t where t.wallet.user.id = :userId and t.type in :types")
    Long sumAmountByUserAndTypes(@Param("userId") Long userId, @Param("types") Collection<WalletTransactionType> types);
}
