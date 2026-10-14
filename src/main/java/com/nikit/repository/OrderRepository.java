package com.nikit.repository;

import com.nikit.domain.OrderStatus;
import com.nikit.domain.OrderType;
import com.nikit.modal.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Sum of (sell price - average cost) x quantity over a user's completed sells, computed in the database. */
    @Query("select coalesce(sum((i.sellPrice - i.buyPrice) * i.quantity), 0) from Order o join o.orderItem i "
            + "where o.user.id = :userId and o.orderType = :type and o.status = :status")
    double sumRealizedPnl(@Param("userId") Long userId, @Param("type") OrderType type, @Param("status") OrderStatus status);

}
