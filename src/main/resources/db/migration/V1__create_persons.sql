CREATE TABLE persons (
    id uuid PRIMARY KEY,
    first_name varchar(255) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name varchar(255),
    patronymic varchar(255),
    gender varchar(255) NOT NULL,
    birth_date date,
    death_date date,
    birth_country varchar(255),
    birth_region varchar(255),
    birth_city varchar(255),
    is_root boolean NOT NULL,
    relationship varchar(255) NOT NULL,
    generation integer NOT NULL,
    spouse_id uuid REFERENCES persons (id) ON DELETE SET NULL,
    CHECK (spouse_id <> id)
);

CREATE TABLE person_parentage (
    parent_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    child_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    PRIMARY KEY (parent_id, child_id),
    CHECK (parent_id <> child_id)
);

CREATE INDEX person_parentage_child_idx ON person_parentage (child_id);
