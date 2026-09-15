package com.localuz.service;

/** Shared by all subscriptions in one scheduler execution; each GET consumes one unit before I/O. */
public final class RecurringReconciliationBudget {
    public static final class Exhausted extends RuntimeException {}
    private final int limit;
    private int used;
    private boolean rateLimited;
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
    public void stopForRateLimit() { rateLimited = true; }
    public boolean rateLimited() { return rateLimited; }
}
