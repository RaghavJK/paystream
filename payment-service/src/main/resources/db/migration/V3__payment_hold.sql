-- A human-approved hold on a flagged payment. Status ON_HOLD fits the existing VARCHAR(16) column.
ALTER TABLE payment ADD COLUMN hold_reason VARCHAR(300);
ALTER TABLE payment ADD COLUMN held_at TIMESTAMPTZ;
