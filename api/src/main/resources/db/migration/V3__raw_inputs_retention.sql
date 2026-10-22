-- raw_inputs are purged after a retention period (RawInputPurgeJob); a confirmed transaction outlives its raw
-- input and just loses the link.
ALTER TABLE transactions DROP CONSTRAINT transactions_raw_input_id_fkey;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_raw_input_id_fkey FOREIGN KEY (raw_input_id) REFERENCES raw_inputs (id)
        ON DELETE SET NULL;
-- the FK action looks transactions up by raw_input_id; the purge selects raw_inputs by age
CREATE INDEX idx_transactions_raw_input ON transactions (raw_input_id);
CREATE INDEX idx_raw_inputs_created ON raw_inputs (created_at);
