package com.nikit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.modal.Asset;
import com.nikit.modal.Coin;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.repository.AssetRepository;
import com.nikit.repository.CoinRepository;
import com.nikit.repository.UserRepository;
import com.nikit.repository.WalletRepository;
import com.nikit.service.CoinService;
import com.nikit.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fires many requests at the same wallet at the same instant to check that balances can't be
 * double-spent. Without row locking, concurrent requests each read the same starting balance,
 * all pass the "enough funds?" check, and the last write wins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyTest {

    private static final int THREADS = 32;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CoinRepository coinRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private WalletService walletService;

    @MockBean private CoinService coinService;

    @BeforeEach
    void setUp() throws Exception {
        Coin coin = new Coin();
        coin.setId("bitcoin");
        coin.setSymbol("btc");
        coin.setName("Bitcoin");
        coin.setCurrentPrice(100.0);
        Coin saved = coinRepository.save(coin);
        when(coinService.findById("bitcoin")).thenReturn(saved);
        when(coinService.getCoinDetails(anyString())).thenReturn("{}");
    }

    // ---------- helpers ----------

    private String signup(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "password123", "fullName", "Racer"));
        String res = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(res).get("jwt").asText();
    }

    private String email() {
        return "racer-" + UUID.randomUUID() + "@test.com";
    }

    private User user(String email) {
        return userRepository.findByEmail(email);
    }

    private Wallet fund(String email, long amount) {
        Wallet wallet = walletService.getUserWallet(user(email));
        wallet.setBalance(BigDecimal.valueOf(amount));
        return walletRepository.save(wallet);
    }

    private BigDecimal balance(String email) {
        return walletRepository.findByUserId(user(email).getId()).getBalance();
    }

    private String order(String type, double quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of("coinId", "bitcoin", "quantity", quantity, "orderType", type));
    }

    /** Sends {@code count} requests as close to simultaneously as possible and returns how many got a 2xx. */
    private int fireConcurrently(int count, IntFunction<RequestBuilder> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                startGate.await();
                return mvc.perform(request.apply(n)).andReturn().getResponse().getStatus();
            }));
        }
        startGate.countDown();
        int ok = 0;
        for (Future<Integer> f : results) {
            int code = f.get(60, TimeUnit.SECONDS);
            assertThat(code).as("unexpected status").isIn(200, 202, 400);
            if (code < 300) ok++;
        }
        pool.shutdown();
        return ok;
    }

    // ---------- tests ----------

    @Test
    void parallelBuysCannotOverspend() throws Exception {
        String email = email();
        String jwt = signup(email);
        fund(email, 500);

        // 100 buys of $10 each against a $500 balance: exactly 50 can succeed
        int ok = fireConcurrently(100, i -> post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(uncheckedOrder("BUY", 0.1)));

        assertThat(ok).isEqualTo(50);
        assertThat(balance(email)).isEqualByComparingTo("0");
        Asset asset = assetRepository.findByUserIdAndCoinId(user(email).getId(), "bitcoin");
        assertThat(asset.getQuantity()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void parallelSellsCannotOversell() throws Exception {
        String email = email();
        String jwt = signup(email);
        fund(email, 100);
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 1.0))).andExpect(status().isOk());

        // 40 sells of 0.1 against a holding of 1.0: at most 10 can succeed
        int ok = fireConcurrently(40, i -> post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(uncheckedOrder("SELL", 0.1)));

        assertThat(ok).isEqualTo(10);
        assertThat(balance(email)).isEqualByComparingTo("100");
    }

    @Test
    void parallelWithdrawalsCannotOverdraw() throws Exception {
        String email = email();
        String jwt = signup(email);
        fund(email, 300);

        int ok = fireConcurrently(100, i -> post("/api/withdrawal/10").header("Authorization", jwt));

        assertThat(ok).isEqualTo(30);
        assertThat(balance(email)).isEqualByComparingTo("0");
    }

    @Test
    void parallelTransfersInBothDirectionsConserveMoneyWithoutDeadlock() throws Exception {
        String alice = email();
        String bob = email();
        String aliceJwt = signup(alice);
        String bobJwt = signup(bob);
        Wallet aliceWallet = fund(alice, 1000);
        Wallet bobWallet = fund(bob, 1000);

        // Opposite-direction transfers are the classic deadlock case if locks are taken in request order
        fireConcurrently(200, i -> i % 2 == 0
                ? put("/api/wallet/" + bobWallet.getId() + "/transfer").header("Authorization", aliceJwt)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 7}")
                : put("/api/wallet/" + aliceWallet.getId() + "/transfer").header("Authorization", bobJwt)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 3}"));

        // 100 x 7 out and 100 x 3 in for Alice, the reverse for Bob; no money created or lost
        assertThat(balance(alice).add(balance(bob))).isEqualByComparingTo("2000");
        assertThat(balance(alice)).isEqualByComparingTo("600");
        assertThat(balance(bob)).isEqualByComparingTo("1400");
    }

    private String uncheckedOrder(String type, double quantity) {
        try {
            return order(type, quantity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
