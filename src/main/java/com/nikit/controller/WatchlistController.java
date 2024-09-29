package com.nikit.controller;

import com.nikit.modal.Coin;
import com.nikit.modal.User;
import com.nikit.modal.Watchlist;
import com.nikit.service.CoinService;
import com.nikit.service.UserService;
import com.nikit.service.WatchListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    @Autowired
    private WatchListService watchListService;

    @Autowired
    private UserService userService;

    @Autowired
    private CoinService coinService;

    @GetMapping("/user")
    public ResponseEntity<Watchlist> getUserWatchlist(
            @RequestHeader("Authorization") String jwt) throws Exception{

        User user = userService.findUserProfileByJwt(jwt);
        Watchlist watchlist = watchListService.findUserWatchlist(user.getId());
        return ResponseEntity.ok(watchlist);
    }

//    @PostMapping("/create")
//    public ResponseEntity<Watchlist> createrWatchlist(
//            @RequestHeader("Authorization") String jwt) throws Exception{
//
//        User user = userService.findUserProfileByJwt(jwt);
//        Watchlist createdWatchlist = watchListService.createWatchlist(user);
//        return ResponseEntity.status(HttpStatus.CREATED).body(createdWatchlist);
//    }

    @GetMapping("/{watchlistId}")
    public ResponseEntity<Watchlist> getWatchlistById(
            @PathVariable Long watchlistId) throws Exception{

        Watchlist watchlist = watchListService.findById(watchlistId);
        return ResponseEntity.ok(watchlist);
    }

    @PatchMapping("/add/coin/{coinId}")
    public ResponseEntity<Coin> addItemToWatchlist(
            @RequestHeader("Authorization") String jwt,
            @PathVariable String coinId) throws Exception{

        User user = userService.findUserProfileByJwt(jwt);
        Coin coin = coinService.findById(coinId);
        Coin addedCoin = watchListService.addItemToWatchlist(coin, user);

        return ResponseEntity.ok(addedCoin);
    }

}
