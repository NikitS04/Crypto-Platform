package com.nikit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikit.domain.PaymentMethod;
import com.nikit.domain.USER_ROLE;
import com.nikit.modal.Asset;
import com.nikit.modal.Coin;
import com.nikit.modal.PaymentOrder;
import com.nikit.modal.User;
import com.nikit.modal.Wallet;
import com.nikit.repository.*;
import com.nikit.service.CoinService;
import com.nikit.service.PaymentService;
import com.nikit.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TradingFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CoinRepository coinRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private WalletService walletService;
    @Autowired private PaymentService paymentService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Market data comes from CoinGecko in production; stub it so tests are deterministic and offline
    @MockBean private CoinService coinService;

    private Coin btc;

    @BeforeEach
    void setUp() throws Exception {
        btc = new Coin();
        btc.setId("bitcoin");
        btc.setSymbol("btc");
        btc.setName("Bitcoin");
        btc.setCurrentPrice(100.0);
        btc = coinRepository.save(btc);

        when(coinService.findById("bitcoin")).thenReturn(btc);
        when(coinService.getCoinDetails(anyString())).thenReturn("{}");
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    private String signup(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "password123", "fullName", "Test User"));
        String res = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(res).get("jwt").asText();
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
        return walletService.getUserWallet(user(email)).getBalance();
    }

    private String order(String type, double quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "coinId", "bitcoin", "quantity", quantity, "orderType", type));
    }

    // ---------- auth ----------

    @Test
    void passwordsAreHashedAndSigninChecksThem() throws Exception {
        String email = uniqueEmail();
        signup(email);

        assertThat(user(email).getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user(email).getPassword())).isTrue();

        mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jwt").isNotEmpty());

        mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customersCannotUseAdminEndpoints() throws Exception {
        String jwt = signup(uniqueEmail());
        mvc.perform(get("/api/admin/withdrawal").header("Authorization", jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/wallet")).andExpect(status().isForbidden());
    }

    // ---------- trading ----------

    @Test
    void buyingWithExactlyEnoughBalanceSucceeds() throws Exception {
        String email = uniqueEmail();
        String jwt = signup(email);
        fund(email, 200);

        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 2)))
                .andExpect(status().isOk());

        assertThat(balance(email)).isEqualByComparingTo("0");
        Asset asset = assetRepository.findByUserIdAndCoinId(user(email).getId(), "bitcoin");
        assertThat(asset.getQuantity()).isEqualTo(2.0);
    }

    @Test
    void failedBuyIsRolledBack() throws Exception {
        String email = uniqueEmail();
        String jwt = signup(email);
        fund(email, 50);

        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient funds for this transaction"));

        assertThat(balance(email)).isEqualByComparingTo("50");
        assertThat(orderRepository.findByUserId(user(email).getId())).isEmpty();
    }

    @Test
    void repeatBuysTrackWeightedAverageCost() throws Exception {
        String email = uniqueEmail();
        String jwt = signup(email);
        fund(email, 1000);

        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 1))).andExpect(status().isOk());
        btc.setCurrentPrice(200.0);
        coinRepository.save(btc);
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 1))).andExpect(status().isOk());

        Asset asset = assetRepository.findByUserIdAndCoinId(user(email).getId(), "bitcoin");
        assertThat(asset.getQuantity()).isEqualTo(2.0);
        assertThat(asset.getBuyPrice()).isEqualTo(150.0);
    }

    @Test
    void sellingAnAssetYouDontOwnIsABadRequest() throws Exception {
        String jwt = signup(uniqueEmail());
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(order("SELL", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Asset not found"));
    }

    @Test
    void cannotSellMoreThanOwned() throws Exception {
        String email = uniqueEmail();
        String jwt = signup(email);
        fund(email, 100);
        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(order("BUY", 1))).andExpect(status().isOk());

        mvc.perform(post("/api/orders/pay").header("Authorization", jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(order("SELL", 5)))
                .andExpect(status().isBadRequest());
        assertThat(balance(email)).isEqualByComparingTo("0");
    }

    // ---------- wallet ----------

    @Test
    void negativeTransferIsRejected() throws Exception {
        String alice = uniqueEmail();
        String bob = uniqueEmail();
        String aliceJwt = signup(alice);
        signup(bob);
        fund(alice, 100);
        Wallet bobWallet = fund(bob, 100);

        mvc.perform(put("/api/wallet/" + bobWallet.getId() + "/transfer").header("Authorization", aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": -50}"))
                .andExpect(status().isBadRequest());

        assertThat(balance(alice)).isEqualByComparingTo("100");
        assertThat(balance(bob)).isEqualByComparingTo("100");
    }

    @Test
    void transferMovesMoneyBetweenWallets() throws Exception {
        String alice = uniqueEmail();
        String bob = uniqueEmail();
        String aliceJwt = signup(alice);
        signup(bob);
        fund(alice, 100);
        Wallet bobWallet = fund(bob, 0);

        mvc.perform(put("/api/wallet/" + bobWallet.getId() + "/transfer").header("Authorization", aliceJwt)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 40}"))
                .andExpect(status().isAccepted());

        assertThat(balance(alice)).isEqualByComparingTo("60");
        assertThat(balance(bob)).isEqualByComparingTo("40");
    }

    @Test
    void cannotDepositUsingAnotherUsersPaymentOrder() throws Exception {
        String alice = uniqueEmail();
        String bob = uniqueEmail();
        signup(alice);
        String bobJwt = signup(bob);
        PaymentOrder aliceOrder = paymentService.createOrder(user(alice), 100L, PaymentMethod.STRIPE);

        mvc.perform(put("/api/wallet/deposit").header("Authorization", bobJwt)
                        .param("order_id", aliceOrder.getId().toString())
                        .param("payment_id", "cs_test_fake"))
                .andExpect(status().isForbidden());
        assertThat(balance(bob)).isEqualByComparingTo("0");
    }

    // ---------- withdrawals ----------

    @Test
    void cannotWithdrawMoreThanBalance() throws Exception {
        String email = uniqueEmail();
        String jwt = signup(email);
        fund(email, 100);

        mvc.perform(post("/api/withdrawal/500").header("Authorization", jwt))
                .andExpect(status().isBadRequest());
        assertThat(balance(email)).isEqualByComparingTo("100");
    }

    @Test
    void declinedWithdrawalRefundsTheRequesterNotTheAdmin() throws Exception {
        String customer = uniqueEmail();
        String customerJwt = signup(customer);
        fund(customer, 100);

        String admin = uniqueEmail();
        signup(admin);
        User adminUser = user(admin);
        adminUser.setRole(USER_ROLE.ROLE_ADMIN);
        userRepository.save(adminUser);
        fund(admin, 0);
        // Sign in again so the token carries the admin role
        String adminRes = mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", admin, "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        String adminJwt = "Bearer " + objectMapper.readTree(adminRes).get("jwt").asText();

        String res = mvc.perform(post("/api/withdrawal/30").header("Authorization", customerJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode withdrawal = objectMapper.readTree(res);
        assertThat(balance(customer)).isEqualByComparingTo("70");

        mvc.perform(patch("/api/admin/withdrawal/" + withdrawal.get("id").asLong() + "/proceed/false")
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawalStatus").value("DECLINE"));

        assertThat(balance(customer)).isEqualByComparingTo("100");
        assertThat(balance(admin)).isEqualByComparingTo("0");

        // A processed withdrawal can't be declined again to refund twice
        mvc.perform(patch("/api/admin/withdrawal/" + withdrawal.get("id").asLong() + "/proceed/false")
                        .header("Authorization", adminJwt))
                .andExpect(status().isBadRequest());
        assertThat(balance(customer)).isEqualByComparingTo("100");
    }
}
