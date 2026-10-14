package com.nikit.controller;

import com.nikit.domain.OrderType;
import com.nikit.modal.Coin;
import com.nikit.modal.Order;
import com.nikit.modal.User;
import com.nikit.modal.WalletTransaction;
import com.nikit.live.LivePrice;
import com.nikit.live.LivePriceStore;
import com.nikit.repository.CoinRepository;
import com.nikit.request.CreateOrderRequest;
import com.nikit.service.CoinService;
import com.nikit.service.OrderService;
import com.nikit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private CoinService coinService;

    @Autowired
    private LivePriceStore livePriceStore;

    @Autowired
    private CoinRepository coinRepository;

    private static final long LIVE_PRICE_MAX_AGE_MS = 15_000;

    @PostMapping("/pay")
    public ResponseEntity<Order> payOrderPayment(
            @RequestHeader("Authorization") String jwt,
            @RequestBody CreateOrderRequest req) throws Exception{

        User user = userService.findUserProfileByJwt(jwt);
        Coin coin = coinRepository.findById(req.getCoinId()).orElse(null);

        // Prefer the streamed price when it is seconds old. Only without one (or for a coin we've never stored)
        // fall back to the market API, whose snapshot can be up to a minute old
        Optional<LivePrice> live = coin == null ? Optional.empty()
                : livePriceStore.get(coin.getSymbol()).filter(p -> p.isFresh(LIVE_PRICE_MAX_AGE_MS));
        if (live.isPresent()) {
            coin.setCurrentPrice(live.get().price());
        } else {
            coinService.getCoinDetails(req.getCoinId());
            coin = coinService.findById(req.getCoinId());
        }

        Order order = orderService.processOrder(coin, req.getQuantity(), req.getOrderType(), user);
        return ResponseEntity.ok(order);


    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrderById(
            @RequestHeader("Authorization") String jwtToken,
            @PathVariable Long orderId) throws Exception{


        User user = userService.findUserProfileByJwt(jwtToken);
        Order order = orderService.getOrderById(orderId);
        if (order.getUser().getId().equals(user.getId())){
            return  ResponseEntity.ok(order);
        } else {
            throw new SecurityException("Invalid User - No Access");
        }
    }

    @GetMapping()
    public ResponseEntity<List<Order>> getAllOrdersForUser(
            @RequestHeader("Authorization") String jwt,
            @RequestParam(required = false) OrderType order_type,
            @RequestParam(required = false) String asset_symbol) throws Exception{
        Long userId = userService.findUserProfileByJwt(jwt).getId();
        List<Order> userOrders = orderService.getAllOrdersOfUser(userId, order_type,asset_symbol);
        return ResponseEntity.ok(userOrders);
    }





}
