
CREATE TABLE IF NOT EXISTS sapiq_metadata.database_metadata (
    id        bigint NOT NULL,
    fqn         varchar(200) NOT NULL,
    service_name varchar(100) NOT NULL,
    name        varchar(200) NOT NULL,
    parent_fqn  varchar(200) NOT NULL,
    hash_data   varchar(500) NULL,
    created_at  timestamp NULL,
    CONSTRAINT database_metadata_pk PRIMARY KEY (id, parent_fqn)
);
CREATE INDEX database_metadata_service_name_idx ON sapiq_metadata.database_metadata USING btree (service_name);

CREATE TABLE IF NOT EXISTS sapiq_metadata.schema_metadata (
    id        bigint NOT NULL,
    fqn         varchar(300) NOT NULL,
    service_name varchar(100) NOT NULL,
    db_name     varchar(200) NOT NULL,
    name        varchar(200) NOT NULL,
    parent_fqn  varchar(300) NOT NULL,
    hash_data   varchar(500) NULL,
    created_at  timestamp NULL,
	CONSTRAINT schema_metadata_pk PRIMARY KEY (id, parent_fqn)
);
CREATE INDEX schema_metadata_service_name_idx ON sapiq_metadata.schema_metadata USING btree (service_name);

CREATE TABLE IF NOT EXISTS sapiq_metadata.table_metadata (
    id        bigint NOT NULL,
    fqn         varchar(400) NOT NULL,
    service_name varchar(100) NOT NULL,
    db_name     varchar(200) NOT NULL,
    schema_name varchar(200) NOT NULL,
    description  varchar(500) NULL,          -- новое поле (описание)
    name        varchar(400) NOT NULL,          -- новое поле (имя таблицы)
    parent_fqn  varchar(400) NOT NULL,          -- новое поле (FQN схемы)
    data        jsonb NULL,                     -- теперь содержит только список колонок
    hash_data   varchar(500) NULL,
    created_at  timestamp NULL,
	CONSTRAINT table_metadata_pk PRIMARY KEY (id, parent_fqn)
);
CREATE INDEX table_metadata_service_name_idx ON sapiq_metadata.table_metadata USING btree (service_name);