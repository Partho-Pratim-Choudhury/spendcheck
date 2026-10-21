-- SpendCheck initial schema. Money is always BIGINT paise.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    name          TEXT        NOT NULL,
    tz            TEXT        NOT NULL DEFAULT 'Asia/Kolkata',
    salary_day    INT         NOT NULL CHECK (salary_day BETWEEN 1 AND 31),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE push_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expo_token TEXT        NOT NULL UNIQUE,
    platform   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);

CREATE TABLE categories (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id  BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name     TEXT    NOT NULL CHECK (length(trim(name)) > 0),
    kind     TEXT    NOT NULL CHECK (kind IN ('FIXED', 'NEED', 'WANT')),
    aliases  TEXT[]  NOT NULL DEFAULT '{}',
    archived BOOLEAN NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX uq_categories_user_lower_name ON categories (user_id, lower(name));

CREATE TABLE budget_plans (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    period_start         DATE        NOT NULL,
    period_end           DATE        NOT NULL,
    income_paise         BIGINT      NOT NULL CHECK (income_paise >= 0),
    savings_target_paise BIGINT      NOT NULL DEFAULT 0 CHECK (savings_target_paise >= 0),
    status               TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (period_end > period_start)
);
CREATE INDEX idx_budget_plans_user_period ON budget_plans (user_id, period_start);
CREATE UNIQUE INDEX uq_budget_plans_one_active ON budget_plans (user_id) WHERE status = 'ACTIVE';

CREATE TABLE plan_limits (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id     BIGINT NOT NULL REFERENCES budget_plans (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    limit_paise BIGINT NOT NULL CHECK (limit_paise >= 0),
    UNIQUE (plan_id, category_id)
);

CREATE TABLE goals (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                       TEXT        NOT NULL,
    target_paise               BIGINT      NOT NULL CHECK (target_paise > 0),
    saved_paise                BIGINT      NOT NULL DEFAULT 0 CHECK (saved_paise >= 0),
    monthly_contribution_paise BIGINT      NOT NULL DEFAULT 0 CHECK (monthly_contribution_paise >= 0),
    target_date                DATE,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user ON goals (user_id);

CREATE TABLE raw_inputs (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       TEXT        NOT NULL CHECK (type IN ('TEXT', 'VOICE')),
    text       TEXT,
    transcript TEXT,
    llm_output JSONB,
    model      TEXT,
    latency_ms INT CHECK (latency_ms >= 0),
    corrected  BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_raw_inputs_user_created ON raw_inputs (user_id, created_at);

CREATE TABLE transactions (
    id           UUID PRIMARY KEY, -- client-generated, enables idempotent create
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id      BIGINT REFERENCES budget_plans (id),
    amount_paise BIGINT      NOT NULL CHECK (amount_paise > 0),
    category_id  BIGINT      NOT NULL REFERENCES categories (id),
    merchant     TEXT,
    note         TEXT,
    spent_at     TIMESTAMPTZ NOT NULL,
    need_or_want TEXT CHECK (need_or_want IN ('NEED', 'WANT')),
    source       TEXT        NOT NULL CHECK (source IN ('TEXT', 'VOICE', 'MANUAL')),
    status       TEXT        NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    raw_input_id BIGINT REFERENCES raw_inputs (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_user_spent_at ON transactions (user_id, spent_at);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category_id);
CREATE INDEX idx_transactions_plan ON transactions (plan_id);

CREATE TABLE spend_checks (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id        BIGINT REFERENCES budget_plans (id),
    amount_paise   BIGINT      NOT NULL CHECK (amount_paise > 0),
    category_id    BIGINT      NOT NULL REFERENCES categories (id),
    verdict        TEXT        NOT NULL CHECK (verdict IN ('GREEN', 'AMBER', 'RED')),
    decision       TEXT        NOT NULL DEFAULT 'UNKNOWN' CHECK (decision IN ('PROCEEDED', 'SKIPPED', 'DEFERRED', 'UNKNOWN')),
    transaction_id UUID REFERENCES transactions (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_spend_checks_user_created ON spend_checks (user_id, created_at);
