package com.nikit.modal;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    private User user;

    // Eager: the watchlist is always returned with its coins, and responses are serialised
    // after the transaction ends (open-in-view is disabled)
    @ManyToMany(fetch = FetchType.EAGER)
    private List<Coin> coins = new ArrayList<>();

}
