package com.nikit.service;

import com.nikit.modal.Coin;
import com.nikit.modal.User;
import com.nikit.modal.Watchlist;

public interface WatchListService {

    Watchlist findUserWatchlist(Long userId) throws Exception;

    Watchlist createWatchlist(User user);

    Watchlist findById(Long id) throws Exception;

    Coin addItemToWatchlist(Coin coin, User user) throws Exception;
}
