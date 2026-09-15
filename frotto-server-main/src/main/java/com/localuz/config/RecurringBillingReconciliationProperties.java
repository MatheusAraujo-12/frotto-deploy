package com.localuz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "billing.recurring-reconciliation")
public class RecurringBillingReconciliationProperties {
    private boolean enabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    private int intervalMinutes = 15;
    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int value) { intervalMinutes = value; }
    private int minIntervalMinutes = 60;
    public int getMinIntervalMinutes() { return minIntervalMinutes; }
    public void setMinIntervalMinutes(int value) { minIntervalMinutes = value; }
    private int batchSize = 10;
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    private int pageSize = 20;
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    private int maxPages = 5;
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int value) { maxPages = value; }
    private int maxDiscoveredItems = 100;
    public int getMaxDiscoveredItems() { return maxDiscoveredItems; }
    public void setMaxDiscoveredItems(int value) { maxDiscoveredItems = value; }
    private int maxHttpCalls = 100;
    public int getMaxHttpCalls() { return maxHttpCalls; }
    public void setMaxHttpCalls(int value) { maxHttpCalls = value; }
    public void validate() {
        if (intervalMinutes < 1 || intervalMinutes > 1440) throw new IllegalArgumentException("Invalid recurring reconciliation intervalMinutes");
        if (minIntervalMinutes < 1 || minIntervalMinutes > 10080) throw new IllegalArgumentException("Invalid recurring reconciliation minIntervalMinutes");
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Invalid recurring reconciliation batchSize");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid recurring reconciliation pageSize");
        if (maxPages < 1 || maxPages > 100) throw new IllegalArgumentException("Invalid recurring reconciliation maxPages");
        if (maxDiscoveredItems < 1 || maxDiscoveredItems > 1000) throw new IllegalArgumentException("Invalid recurring reconciliation maxDiscoveredItems");
        if (maxHttpCalls < 1 || maxHttpCalls > 1000) throw new IllegalArgumentException("Invalid recurring reconciliation maxHttpCalls");
    }
}

