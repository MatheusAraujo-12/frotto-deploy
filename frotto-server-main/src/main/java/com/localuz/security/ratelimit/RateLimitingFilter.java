package com.localuz.security.ratelimit;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory rate limiter for the unauthenticated auth endpoints (login/registration), added by
 * the security audit remediation to mitigate brute-force / credential-stuffing / registration
 * abuse. Deliberately dependency-free (no Bucket4j/Redis) to keep this change conservative.
 *
 * Limitation: counters live in this JVM's heap only. On a multi-instance deployment each instance
 * enforces its own limit independently (not shared), and counters reset on redeploy/restart. If
 * the app is ever horizontally scaled, replace this with a shared store (e.g. Redis).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // POST /api/authenticate: 10 tentativas por minuto por IP.
    private static final String AUTHENTICATE_PATH = "/api/authenticate";
    private static final int AUTHENTICATE_CAPACITY = 10;

    // POST /api/register: 5 tentativas por minuto por IP (mais restritivo — criação de conta é
    // um alvo mais valioso para abuso automatizado do que uma tentativa de login legítima).
    private static final String REGISTER_PATH = "/api/register";
    private static final int REGISTER_CAPACITY = 5;

    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);
    // Entradas mais antigas que isso são consideradas frias e removidas do mapa, para o processo
    // de longa duração não acumular um IP por linha indefinidamente.
    private static final long STALE_AFTER_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    public RateLimitingFilter() {
        cleanupExecutor.scheduleAtFixedRate(this::evictStaleBuckets, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        int capacity;
        if (AUTHENTICATE_PATH.equals(path)) {
            capacity = AUTHENTICATE_CAPACITY;
        } else if (REGISTER_PATH.equals(path)) {
            capacity = REGISTER_CAPACITY;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = clientIp + "|" + path;
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        if (!bucket.tryConsume(capacity)) {
            log.warn("Rate limit exceeded for {} on {} (capacity {}/min)", clientIp, path, capacity);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"Muitas tentativas. Tente novamente em instantes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the client IP behind Cloudflare + Traefik/Nginx.
     *
     * IMPORTANT (documentar na infraestrutura, não corrigido neste código): estes headers só são
     * confiáveis se a borda (Traefik/Nginx) aceitar tráfego HTTP somente vindo dos ranges de IP da
     * Cloudflare. Caso contrário, um cliente pode forjar CF-Connecting-IP / X-Forwarded-For e
     * contornar o rate limit. Esse bloqueio de rede é uma configuração de infraestrutura fora do
     * escopo desta etapa (ver relatório final, seção de pendências).
     */
    private String extractClientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cfConnectingIp)) {
            return cfConnectingIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > STALE_AFTER_MILLIS);
    }

    /** Fixed-window counter for a single IP+endpoint combination. */
    private static final class Bucket {

        private volatile long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean tryConsume(int capacity) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }
            if (count >= capacity) {
                return false;
            }
            count++;
            return true;
        }
    }
}
