CREATE TABLE IF NOT EXISTS  eg_ndb_push_status(
    id SERIAL PRIMARY KEY,
    response_hash TEXT,
    no_of_reccords integer,
    error TEXT,
    environment character varying(32),
    message_description character varying(128),
    data_pushed_date DATE,
    createddate timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);