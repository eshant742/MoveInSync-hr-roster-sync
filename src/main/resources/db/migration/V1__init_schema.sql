-- ============================================================
-- V1__init_schema.sql
-- HR Roster Sync — Initial database schema
-- ============================================================

-- ── Employees (main entity) ──────────────────────────────────
CREATE TABLE employees (
    id                      BIGSERIAL       PRIMARY KEY,
    employee_id             VARCHAR(64)     NOT NULL,       -- Business ID from HR system
    tenant_id               VARCHAR(64)     NOT NULL,       -- Multi-tenant support
    version                 BIGINT          NOT NULL DEFAULT 0, -- HR system version for ordering

    -- HR-owned fields
    first_name              VARCHAR(128)    NOT NULL,
    last_name               VARCHAR(128)    NOT NULL,
    email                   VARCHAR(256)    NOT NULL,
    phone                   VARCHAR(20),
    manager_id              VARCHAR(64),
    office_code             VARCHAR(32),
    department              VARCHAR(128),
    shift_code              VARCHAR(32),

    -- Transport-admin-owned fields
    pickup_point            VARCHAR(256),
    special_assistance_needs VARCHAR(512),

    -- Status & Audit
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    jpa_version             BIGINT          DEFAULT 0,

    CONSTRAINT uk_employee_tenant UNIQUE (employee_id, tenant_id)
);

CREATE INDEX idx_employee_tenant ON employees(employee_id, tenant_id);
CREATE INDEX idx_tenant ON employees(tenant_id);
CREATE INDEX idx_office_code ON employees(office_code);
CREATE INDEX idx_active ON employees(active);

-- ── Outbox Events (reliable event publishing) ────────────────
CREATE TABLE outbox_events (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        VARCHAR(64)     NOT NULL UNIQUE,
    aggregate_type  VARCHAR(64)     NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    payload         TEXT            NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);

CREATE INDEX idx_outbox_published ON outbox_events(published, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- ── Reconciliation Runs ──────────────────────────────────────
CREATE TABLE reconciliation_runs (
    id                      BIGSERIAL           PRIMARY KEY,
    tenant_id               VARCHAR(64)         NOT NULL,
    status                  VARCHAR(32)         NOT NULL, -- RUNNING, COMPLETED, SAFETY_BRAKE_TRIGGERED, FAILED
    total_in_export         BIGINT              DEFAULT 0,
    total_in_store          BIGINT              DEFAULT 0,
    matched                 BIGINT              DEFAULT 0,
    created_count           BIGINT              DEFAULT 0,
    updated_count           BIGINT              DEFAULT 0,
    deactivated_count       BIGINT              DEFAULT 0,
    skipped                 BIGINT              DEFAULT 0,
    error_count             BIGINT              DEFAULT 0,
    missing_percentage      DOUBLE PRECISION,
    safety_brake_triggered  BOOLEAN             DEFAULT FALSE,
    operator_approved       BOOLEAN             DEFAULT FALSE,
    operator_approved_by    VARCHAR(100),
    started_at              TIMESTAMP           NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    error_details           TEXT
);

CREATE INDEX idx_recon_status ON reconciliation_runs(status);
CREATE INDEX idx_recon_tenant ON reconciliation_runs(tenant_id);
CREATE INDEX idx_recon_started_at ON reconciliation_runs(started_at);

-- ── Ingestion Errors (no PII — employee_id only) ────────────
CREATE TABLE ingestion_errors (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    employee_id     VARCHAR(64),
    error_type      VARCHAR(64)     NOT NULL,
    error_message   TEXT            NOT NULL,
    source          VARCHAR(32)     NOT NULL,       -- API or NIGHTLY_EXPORT
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ingest_err_tenant ON ingestion_errors(tenant_id);
CREATE INDEX idx_ingest_err_employee ON ingestion_errors(employee_id);
CREATE INDEX idx_ingest_err_source ON ingestion_errors(source);
CREATE INDEX idx_ingest_err_created ON ingestion_errors(created_at);
