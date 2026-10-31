-- ============================================================
-- V2__schema_alignment.sql
-- Add missing columns to ingestion_errors and reconciliation_runs
-- ============================================================

-- Add reconciliation_run_id to ingestion_errors
ALTER TABLE ingestion_errors
    ADD COLUMN IF NOT EXISTS reconciliation_run_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_ingest_err_recon ON ingestion_errors(reconciliation_run_id);

-- Add approved_by, approval_reason, message to reconciliation_runs
ALTER TABLE reconciliation_runs
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS approval_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS message VARCHAR(1024);

