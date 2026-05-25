CREATE TABLE IF NOT EXISTS paytype_master
(
    id SERIAL PRIMARY KEY,
    ulb_tenantid character varying(56) NOT NULL,
    charges_type_name character varying(256) NOT NULL,
    payment_type character varying(10) NOT NULL,
    defunt character varying(2),
    createdby character varying(128),
	createddate timestamp without time zone,
	updatedby character varying(128),
	updateddate timestamp without time zone,
	optflag character varying(2),
	hrnh character varying(2),
	depflag character varying(2),
	fdrflg character varying(2),
	zdaflg character varying(2),
	UNIQUE (ulb_tenantid, charges_type_name)
);


CREATE TABLE IF NOT EXISTS fee_details
(
    id SERIAL PRIMARY KEY,
    paytype_id integer,
    feetype character varying(5),
    srno integer,
    ulb_tenantid character varying(56),
    bill_id character varying(64),
    application_no character varying(64),
    unit character varying(56),
    charges_type_name character varying(256),
    prop_plot_area numeric(15,2),
    amount numeric(15,2),
    rate numeric(15,2),
    type character varying(56),
    createdby character varying(128),
    createddate timestamp without time zone,
    updatedby character varying(128),
    updateddate timestamp without time zone
);

CREATE TABLE IF NOT EXISTS proposal_type_master
(
    id SERIAL PRIMARY KEY,
	ulb_tenantid character varying(56) NOT NULL,
	description character varying(100) NOT NULL,
	defunt character varying(2) NOT NULL,
	createdby character varying(128) NOT NULL,
	createddate timestamp without time zone,
	updatedby character varying(128),
	updateddate timestamp without time zone
);


CREATE TABLE IF NOT EXISTS bcategory_master
(
    id SERIAL PRIMARY KEY,
	ulb_tenantid character varying(56) NOT NULL,
	description character varying(100) NOT NULL,
	defunt character varying(2) NOT NULL,
	createdby character varying(128) NOT NULL,
	createddate timestamp without time zone,
	updatedby character varying(128),
	updateddate timestamp without time zone
);


CREATE TABLE IF NOT EXISTS bscategory_master
(
    id SERIAL PRIMARY KEY,
	catid integer NOT NULL,
	ulb_tenantid character varying(56) NOT NULL,
	description character varying(100) NOT NULL,
	defunt character varying(2) NOT NULL,
	createdby character varying(128) NOT NULL,
	createddate timestamp without time zone,
	updatedby character varying(128),
	updateddate timestamp without time zone,
	CONSTRAINT fk_bscategory_master_id FOREIGN KEY (catid)
        REFERENCES bcategory_master(id) MATCH SIMPLE
);



CREATE TABLE IF NOT EXISTS pay_tp_rate_master
(
    id SERIAL PRIMARY KEY,
    ulb_tenantid character varying(56) NOT NULL,
	unitid character varying(40) NOT NULL,
	typeid integer NOT NULL,
	srno bigint NOT NULL,
	calcon character varying(56) NOT NULL,
    calcact character varying(56) NOT NULL,
	p_category integer,
	b_category integer,
	s_category integer,
	rate_res numeric(10,2),
	rate_comm numeric(10,2),
	rate_ind numeric(10,2),
	perval numeric(10),
	createdby character varying(128) NOT NULL,
	createddate timestamp without time zone
);

CREATE TABLE IF NOT EXISTS slab_master
(
	id SERIAL PRIMARY KEY,
	ulb_tenantid character varying(56) NOT NULL,
	paytype_id integer NOT NULL,
	srno bigint NOT NULL,
	from_val numeric(10,2) NOT NULL,
	to_val numeric(10,2),
	rate_res numeric(10,2),
	rate_comm numeric(10,2),
	rate_ind numeric(10,2),
	operation character varying(32),
	p_category integer,
	b_category integer,
	s_category integer,
	createdby character varying(128) NOT NULL,
	createddate timestamp without time zone NOT NULL,
	multp_val numeric(10,2),
	max_limit numeric(10,2)
);

ALTER TABLE fee_details
ADD COLUMN verify character varying(2);

ALTER TABLE fee_details
ADD COLUMN verifiedby character varying(128),
ADD COLUMN verifieddate timestamp without time zone;

ALTER TABLE proposal_type_master
ADD COLUMN srno integer;

ALTER TABLE fee_details
ADD COLUMN is_fdr character varying(2) DEFAULT 'N';