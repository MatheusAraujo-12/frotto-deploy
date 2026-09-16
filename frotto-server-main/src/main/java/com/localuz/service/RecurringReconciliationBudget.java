package com.localuz.service;

/** Shared by all subscriptions in one scheduler execution; each GET consumes one unit before I/O. */
public final class RecurringReconciliationBudget {
    public static final class Exhausted extends RuntimeException {}
    private final int limit;
    private int used;
    private boolean rateLimited;
    private Integer retryAfterSeconds;
    public RecurringReconciliationBudget(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive HTTP budget required");
        this.limit = limit;
    }
    public void beforeHttp() {
        if (!available()) throw new Exhausted();
        used++;
    }
    public boolean available() { return !rateLimited && used < limit; }
    public int used() { return used; }
    /** retryAfterSeconds is the provider's Retry-After header value (seconds), if it sent a usable one. */
    public void stopForRateLimit(Integer retryAfterSeconds) { rateLimited = true; this.retryAfterSeconds = retryAfterSeconds; }
    public boolean rateLimited() { return rateLimited; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
