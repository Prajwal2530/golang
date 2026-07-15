INSERT INTO eg_user (id, uuid, tenantid, username, password, mobilenumber, active, type, createddate, lastmodifieddate, createdby, lastmodifiedby, pwdexpirydate)
VALUES (
  (SELECT COALESCE(MAX(id),0)+1 FROM eg_user),
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

INSERT INTO eg_userrole_v1 (role_code, role_tenantid, user_tenantid, user_id)
SELECT 'SUPERUSER', 'pb.amritsar', 'pb.amritsar', id FROM eg_user WHERE username='admin2';

INSERT INTO eg_userrole_v1 (role_code, role_tenantid, user_tenantid, user_id)
SELECT 'BPA_BUILDER', 'pb.amritsar', 'pb.amritsar', id FROM eg_user WHERE username='admin2';

INSERT INTO eg_userrole_v1 (role_code, role_tenantid, user_tenantid, user_id)
SELECT 'BPA_VERIFIER', 'pb.amritsar', 'pb.amritsar', id FROM eg_user WHERE username='admin2';
