CREATE TABLE IF NOT EXISTS eg_userrole_v1 (
  rolecode VARCHAR(64),
  tenantid VARCHAR(256),
  user_id BIGINT REFERENCES eg_user(id),
  user_tenantid VARCHAR(256),
  PRIMARY KEY (rolecode, tenantid, user_id)
);

CREATE TABLE IF NOT EXISTS eg_userrole_v2 (
  rolecode VARCHAR(64),
  tenantid VARCHAR(256),
  user_id BIGINT REFERENCES eg_user(id),
  PRIMARY KEY (rolecode, tenantid, user_id)
);

CREATE TABLE IF NOT EXISTS eg_user_address (
  id BIGSERIAL PRIMARY KEY,
  type VARCHAR(32),
  addressline1 VARCHAR(256),
  addressline2 VARCHAR(256),
  city VARCHAR(256),
  pincode VARCHAR(16),
  tenantid VARCHAR(256),
  userid BIGINT REFERENCES eg_user(id)
);

INSERT INTO eg_user (uuid, tenantid, username, password, mobilenumber, active, type, createddate, lastmodifieddate, createdby, lastmodifiedby, pwdexpirydate)
VALUES (
  'a35a1e7a-7e5c-11ee-b962-0242ac120002',
  'pb.amritsar',
  'admin2',
  '$2a$10$yQ5T.9ryV.RfWD9SXDRK..K5M1YhT5FN5VkxJZ.jVVqSSJjxhGfmO',
  '9999999999',
  true,
  'EMPLOYEE',
  NOW(), NOW(), 1, 1,
  NOW() + INTERVAL '1 year'
);

INSERT INTO eg_userrole_v1 (rolecode, tenantid, user_tenantid, user_id)
SELECT 'SUPERUSER', 'pb.amritsar', 'pb.amritsar', id FROM eg_user WHERE username='admin2';

INSERT INTO eg_userrole_v2 (rolecode, tenantid, user_id)
SELECT 'SUPERUSER', 'pb.amritsar', id FROM eg_user WHERE username='admin2';
