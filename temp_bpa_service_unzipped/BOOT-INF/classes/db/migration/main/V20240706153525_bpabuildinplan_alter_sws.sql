ALTER TABLE eg_bpa_buildingplan
ADD COLUMN swsapplicationid bigint,
ADD COLUMN swsunitregistrationno bigint,
ADD COLUMN isswsapplication boolean;


ALTER TABLE eg_bpa_auditdetails
ADD COLUMN swsapplicationid bigint,
ADD COLUMN swsunitregistrationno bigint,
ADD COLUMN isswsapplication boolean;