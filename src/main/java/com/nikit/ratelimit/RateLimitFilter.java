package com.nikit.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies per-client request limits before a request reaches any controller.
 *
 *  - /auth/** (sign in, sign up, password reset): limited per IP address, tightly, to slow down
 *    password guessing and OTP brute force.
 *  - Everything else: limited per signed-in user (per IP when anonymous).
 *
 * Over the limit returns 429 with Retry-After. If Redis is unavailable the filter fails open:
 * an outage of the limiter should degrade protection, not take the whole API down.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String KEY_PREFIX = "trading:ratelimit:";

    public record Policy(int capacity, double refillPerSecond) {
    }

    private final RateLimiter limiter;
    private final Policy authPolicy;
    private final Policy apiPolicy;

    public RateLimitFilter(RateLimiter limiter, Policy authPolicy, Policy apiPolicy) {
        this.limiter = limiter;
        this.authPolicy = authPolicy;
        this.apiPolicy = apiPolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod())
                || path.startsWith("/ws/")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isAuth = request.getRequestURI().startsWith("/auth/");
        Policy policy = isAuth ? authPolicy : apiPolicy;
        String key = KEY_PREFIX + (isAuth ? "auth:ip:" + request.getRemoteAddr() : "api:" + clientIdentity(request));

        RateLimiter.Decision decision;
        try {
            decision = limiter.tryAcquire(key, policy.capacity(), policy.refillPerSecond());
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        long retrySeconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retrySeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Too many requests, please retry in " + retrySeconds + "s\"}");
    }

    private static String clientIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
