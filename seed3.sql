ALTER TABLE eg_user_address ADD COLUMN IF NOT EXISTS createddate TIMESTAMP;
ALTER TABLE eg_user_address ADD COLUMN IF NOT EXISTS lastmodifieddate TIMESTAMP;
ALTER TABLE eg_user_address ADD COLUMN IF NOT EXISTS createdby BIGINT;
ALTER TABLE eg_user_address ADD COLUMN IF NOT EXISTS lastmodifiedby BIGINT;

CREATE TABLE IF NOT EXISTS eg_user_login_failed_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_uuid VARCHAR(64),
    active BOOLEAN DEFAULT true,
    attempt_date TIMESTAMP,
    createdby BIGINT,
    createddate TIMESTAMP,
    lastmodifiedby BIGINT,
    lastmodifieddate TIMESTAMP,
    tenantid VARCHAR(256)
);
