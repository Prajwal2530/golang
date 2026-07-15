INSERT INTO eg_citypreferences (id, tenantid, municipality_name, municipality_contact_no, municipality_address, municipality_contact_email, version, createdby, lastmodifiedby)
VALUES (3, 'pb.amritsar', 'Amritsar Municipal Corporation', '0183-2500000', 'Town Hall, Amritsar, Punjab', 'admin@amritsar.gov.in', 1, 1, 1)
ON CONFLICT DO NOTHING;

INSERT INTO eg_city (id, name, local_name, domainurl, code, district_code, district_name, region_name, grade, latitude, longitude, tenantid, active, version, createdby, lastmodifiedby, preferences)
VALUES (3, 'Amritsar', 'Amritsar', 'pb.amritsar', 'AMR', 'AMR', 'Amritsar', 'Punjab', 'G', 31.6340, 74.8723, 'pb.amritsar', true, 1, 1, 1, 3)
ON CONFLICT DO NOTHING;
