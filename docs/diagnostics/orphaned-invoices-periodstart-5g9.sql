-- 5G.9 section 10/E — read-only diagnostic query for staging.
--
-- Purpose: find BillingInvoice rows with period_start IS NULL (an "unanchored" invoice -
-- see SubscriptionFinancialCoverageService's fail-closed handling, docs/billing-production-
-- closure-5g9.md section E), the user/subscription they belong to, and how many such rows exist
-- per subscription. This is used to assess whether the fail-closed behavior is actually blocking
-- any real, currently-paying customer in staging/production - it never fixes or removes anything.
--
-- SAFETY: every statement below is a plain SELECT. Nothing here writes, deletes, or locks any
-- row. Safe to run against a production replica or staging database with a read-only credential.
-- Do NOT run any of this against production directly unless your own operational policy allows
-- read-only ad-hoc queries there; a read replica is preferred.

-- 1) Every orphaned invoice (period_start IS NULL), with its subscription and user.
SELECT
    bi.id                              AS billing_invoice_id,
    bi.status                          AS billing_invoice_status,
    bi.amount                          AS billing_invoice_amount,
    bi.currency                        AS billing_invoice_currency,
    bi.external_authorized_payment_id  AS external_authorized_payment_id,
    bi.provider_status                 AS provider_status,
    bi.created_at                      AS billing_invoice_created_at,
    bi.updated_at                      AS billing_invoice_updated_at,
    bi.last_reconciled_at              AS last_reconciled_at,
    s.id                               AS subscription_id,
    s.status                           AS subscription_status,
    s.source                           AS subscription_source,
    s.external_subscription_id         AS subscription_external_id,
    s.start_date                       AS subscription_start_date,
    s.current_period_end               AS subscription_current_period_end,
    s.canceled_at                      AS subscription_canceled_at,
    u.id                               AS user_id,
    u.login                            AS user_login,
    u.email                            AS user_email
FROM billing_invoice bi
JOIN subscription s ON s.id = bi.subscription_id
JOIN jhi_user u ON u.id = s.jhi_user_id
WHERE bi.period_start IS NULL
ORDER BY bi.created_at ASC;

-- 2) Count of orphaned invoices per subscription (helps spot a subscription with many orphaned
--    rows vs. a single one-off), plus the subscription's own status/source for context.
SELECT
    s.id                        AS subscription_id,
    s.status                    AS subscription_status,
    s.source                    AS subscription_source,
    s.external_subscription_id  AS subscription_external_id,
    u.id                        AS user_id,
    u.login                     AS user_login,
    COUNT(*)                    AS orphaned_invoice_count
FROM billing_invoice bi
JOIN subscription s ON s.id = bi.subscription_id
JOIN jhi_user u ON u.id = s.jhi_user_id
WHERE bi.period_start IS NULL
GROUP BY s.id, s.status, s.source, s.external_subscription_id, u.id, u.login
ORDER BY orphaned_invoice_count DESC, subscription_id ASC;

-- 3) The specific case that matters most operationally: a subscription that has BOTH an orphaned
--    invoice AND at least one fully-anchored, currently-paid invoice (period_start IS NOT NULL,
--    status = 'PAID') - this is exactly the SubscriptionFinancialCoverageServiceTest reproduction
--    (historicalInvoiceWithNullPeriodStartBlocksAnOtherwiseValidCurrentPaidInvoice) and identifies
--    a real customer who may be losing access today because of an orphaned historical row.
SELECT DISTINCT
    s.id                        AS subscription_id,
    s.status                    AS subscription_status,
    s.external_subscription_id AS subscription_external_id,
    u.id                        AS user_id,
    u.login                     AS user_login,
    u.email                     AS user_email
FROM subscription s
JOIN jhi_user u ON u.id = s.jhi_user_id
WHERE EXISTS (
    SELECT 1 FROM billing_invoice bi_null
    WHERE bi_null.subscription_id = s.id AND bi_null.period_start IS NULL
)
AND EXISTS (
    SELECT 1 FROM billing_invoice bi_paid
    WHERE bi_paid.subscription_id = s.id
      AND bi_paid.period_start IS NOT NULL
      AND bi_paid.status = 'PAID'
);
