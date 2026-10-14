package com.nikit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth.capacity=3",
        "app.rate-limit.auth.refill-per-second=0.01",
        "app.rate-limit.api.capacity=5",
        "app.rate-limit.api.refill-per-second=0.01"
})
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired private MockMvc mvc;

    private MockHttpServletResponse signinFrom(String ip) throws Exception {
        return mvc.perform(post("/auth/signin").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.com\",\"password\":\"wrong\"}"))
                .andReturn().getResponse();
    }

    @Test
    void signinAttemptsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(signinFrom("10.0.0.1").getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse limited = signinFrom("10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        assertThat(limited.getContentAsString()).contains("Too many requests");

        // Another client is unaffected
        assertThat(signinFrom("10.0.0.2").getStatus()).isEqualTo(401);
    }

    @Test
    void apiRequestsCarryRemainingQuotaAndAreCappedAtCapacity() throws Exception {
        int ok = 0;
        int limited = 0;
        String lastRemaining = null;
        for (int i = 0; i < 8; i++) {
            MockHttpServletResponse res = mvc.perform(get("/coins/live").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; }))
                    .andReturn().getResponse();
            if (res.getStatus() == 200) {
                ok++;
                lastRemaining = res.getHeader("X-RateLimit-Remaining");
            } else if (res.getStatus() == 429) {
                limited++;
            }
        }
        assertThat(ok).isEqualTo(5);
        assertThat(limited).isEqualTo(3);
        assertThat(lastRemaining).isEqualTo("0");
    }
}
