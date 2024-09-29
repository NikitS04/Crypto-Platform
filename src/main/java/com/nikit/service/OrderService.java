package com.nikit.service;

import com.nikit.modal.Coin;
import com.nikit.modal.User;
import com.nikit.modal.Order;
import com.nikit.modal.OrderItem;
import com.nikit.domain.OrderType;

import java.util.List;

public interface OrderService {

    Order createOrder(User user, OrderItem orderItem, OrderType orderType);

    Order getOrderById(Long orderId) throws Exception;

    List<Order> getAllOrdersOfUser(Long userId, OrderType orderType, String assetSymbol);



    Order processOrder(Coin coin, double quantity, OrderType orderType, User user) throws Exception;






}
