-- Dashboard/report lookups of a plan's spend checks (pauses saved, skipped count).
CREATE INDEX idx_spend_checks_plan ON spend_checks (plan_id);
-- Plan limits are always loaded by plan; category_id FK lookups (category usage) get their own index.
CREATE INDEX idx_plan_limits_category ON plan_limits (category_id);
