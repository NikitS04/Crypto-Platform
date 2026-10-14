package com.nikit.service;

import com.nikit.analytics.PortfolioChangedEvent;
import com.nikit.domain.OrderStatus;
import com.nikit.domain.OrderType;
import com.nikit.modal.*;
import com.nikit.repository.OrderItemRepository;
import com.nikit.repository.OrderRepository;
import com.nikit.domain.WalletTransactionType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private AssetService assetService;

    @Autowired
    private WalletTransactionService walletTransactionService;

    @Autowired
    private ApplicationEventPublisher events;

    @Override
    public Order createOrder(User user, OrderItem orderItem, OrderType orderType) {
        double price = orderItem.getCoin().getCurrentPrice()*orderItem.getQuantity();

        Order order = new Order();
        order.setUser(user);
        order.setOrderItem(orderItem);
        order.setOrderType(orderType);
        order.setPrice(BigDecimal.valueOf(price));
        order.setTimestamp(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);


        return orderRepository.save(order);
    }

    @Override
    public Order getOrderById(Long orderId) throws Exception {
        return orderRepository.findById(orderId)
                .orElseThrow(()->
                        new Exception("Order not found"));
    }

    @Override
    public List<Order> getAllOrdersOfUser(Long userId, OrderType orderType, String assetSymbol) {
        return orderRepository.findByUserId(userId);
    }

    private OrderItem createOrderItem(Coin coin, double quatity, double buyPrice, double sellPrice){
        OrderItem orderItem = new OrderItem();
        orderItem.setCoin(coin);
        orderItem.setQuantity(quatity);
        orderItem.setBuyPrice(buyPrice);
        orderItem.setSellPrice(sellPrice);

        return orderItemRepository.save(orderItem);

    }

    @Transactional(rollbackFor = Exception.class)
    public Order buyAsset(Coin coin, double quantity, User user) throws Exception {
        if(quantity<=0){
            throw new IllegalArgumentException("Quantity should be > 0");
        }
        double buyPrice = coin.getCurrentPrice();

        OrderItem orderItem = createOrderItem(coin, quantity, buyPrice, 0);

        Order order = createOrder(user, orderItem, OrderType.BUY);
        orderItem.setOrder(order);

        Wallet wallet = walletService.payOrderPayment(order, user);

        order.setStatus(OrderStatus.SUCCESS);
        order.setOrderType(OrderType.BUY);
        Order savedOrder = orderRepository.save(order);

        Asset oldAsset = assetService.findAssetByUserIdAndCoinId(user.getId(), coin.getId());
        if (oldAsset == null){
            assetService.createAsset(user, coin, quantity);
        } else {
            // Keep buyPrice as the weighted average cost so P&L stays correct across repeat buys
            double totalQuantity = oldAsset.getQuantity() + quantity;
            double averageCost = (oldAsset.getQuantity()*oldAsset.getBuyPrice() + quantity*buyPrice) / totalQuantity;
            assetService.updateAsset(oldAsset.getId(), quantity, averageCost);
        }

        recordTrade(wallet, WalletTransactionType.BUY_ASSET, order, "Bought " + quantity + " " + coin.getSymbol().toUpperCase());
        return savedOrder;

    }

    @Transactional(rollbackFor = Exception.class)
    public Order sellAsset(Coin coin, double quantity, User user) throws Exception {
        if(quantity<=0){
            throw new IllegalArgumentException("Quantity should be > 0");
        }
        double sellPrice = coin.getCurrentPrice();

        Asset assetToSell = assetService.findAssetByUserIdAndCoinId(
                user.getId(),
                coin.getId());

        if (assetToSell == null){
            throw new IllegalArgumentException("Asset not found");
        }
        if(assetToSell.getQuantity() < quantity){
            throw new IllegalArgumentException("Insufficient Quantity to Sell");
        }

        OrderItem orderItem = createOrderItem(coin,
                quantity,
                assetToSell.getBuyPrice(),
                sellPrice);

        Order order = createOrder(user, orderItem, OrderType.SELL);
        orderItem.setOrder(order);

        order.setStatus(OrderStatus.SUCCESS);
        order.setOrderType(OrderType.SELL);
        Order savedOrder = orderRepository.save(order);
        Wallet wallet = walletService.payOrderPayment(order, user);

        Asset updatedAsset = assetService.updateAsset(assetToSell.getId(), -quantity, assetToSell.getBuyPrice());

        if (updatedAsset.getQuantity()*coin.getCurrentPrice()<=1){
            assetService.deleteAsset(updatedAsset.getId());
        }

        recordTrade(wallet, WalletTransactionType.SELL_ASSET, order, "Sold " + quantity + " " + coin.getSymbol().toUpperCase());
        return savedOrder;
    }

    private void recordTrade(Wallet wallet, WalletTransactionType type, Order order, String purpose) throws Exception {
        long amount = order.getPrice().setScale(0, RoundingMode.HALF_UP).longValue();
        walletTransactionService.createTransaction(wallet.getId(), type,
                type == WalletTransactionType.BUY_ASSET ? -amount : amount,
                String.valueOf(order.getId()), purpose);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order processOrder(Coin coin, double quantity, OrderType orderType, User user) throws Exception {
        // Lock the user's wallet before reading their balance or holdings. Every trade for this user
        // then runs one at a time, which also protects the asset row from concurrent buys/sells.
        walletService.lockUserWallet(user);
        // Delivered after commit, so a failed or rolled-back trade never produces a snapshot
        events.publishEvent(new PortfolioChangedEvent(user.getId()));
        if (orderType == OrderType.BUY){
            return buyAsset(coin,quantity,user);
        }
        else if (orderType == OrderType.SELL){
            return sellAsset(coin,quantity,user);
        }
        throw new IllegalArgumentException("Invalid Order Type");

    }
}
