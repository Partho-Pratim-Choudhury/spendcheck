package com.parthopratim.spendcheck.common;

import com.parthopratim.spendcheck.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the per-user AI rate limit on the Groq-calling endpoints. Runs inside the DispatcherServlet, i.e. after
 * the security filter chain, so the authenticated user id is already in the {@code SecurityContext}.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final AiRateLimiter limiter;
    private final CurrentUser currentUser;

    public RateLimitInterceptor(AiRateLimiter limiter, CurrentUser currentUser) {
        this.limiter = limiter;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AiRateLimiter.Decision decision = limiter.check(currentUser.id());
        if (!decision.allowed()) {
            throw new TooManyRequestsException("Too many requests — give it a moment.",
                    decision.retryAfterSeconds());
        }
        return true;
    }
}
