-- V1 parentage has no type. Require explicit classification before removing the source.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM person_parentage) THEN
        RAISE EXCEPTION 'V2 requires explicit classification of V1 parentage before migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM persons
        WHERE relationship NOT IN ('YOU', 'NONE') AND NOT (
            relationship IN ('HUSBAND', 'WIFE') AND spouse_id IS NOT NULL
        )
    ) THEN
        RAISE EXCEPTION 'V2 found legacy kinship labels without an unambiguous relationship';
    END IF;
END $$;

ALTER TABLE persons
    ADD COLUMN birth_surname varchar(255),
    ADD COLUMN notes text,
    ADD COLUMN birth_date_precision varchar(16),
    ADD COLUMN death_date_precision varchar(16),
    ADD CONSTRAINT persons_gender_check CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN'));

UPDATE persons SET birth_date_precision = 'DAY' WHERE birth_date IS NOT NULL;
UPDATE persons SET death_date_precision = 'DAY' WHERE death_date IS NOT NULL;

ALTER TABLE persons
    ADD CONSTRAINT persons_birth_date_precision_check CHECK (
        (birth_date IS NULL AND birth_date_precision IS NULL) OR
        (birth_date IS NOT NULL AND birth_date_precision IS NOT NULL
            AND birth_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND CASE birth_date_precision
                WHEN 'YEAR' THEN EXTRACT(MONTH FROM birth_date) = 1 AND EXTRACT(DAY FROM birth_date) = 1
                WHEN 'MONTH' THEN EXTRACT(DAY FROM birth_date) = 1
                WHEN 'DAY' THEN true
                ELSE false
            END)
    ),
    ADD CONSTRAINT persons_death_date_precision_check CHECK (
        (death_date IS NULL AND death_date_precision IS NULL) OR
        (death_date IS NOT NULL AND death_date_precision IS NOT NULL
            AND death_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND CASE death_date_precision
                WHEN 'YEAR' THEN EXTRACT(MONTH FROM death_date) = 1 AND EXTRACT(DAY FROM death_date) = 1
                WHEN 'MONTH' THEN EXTRACT(DAY FROM death_date) = 1
                WHEN 'DAY' THEN true
                ELSE false
            END)
    );

CREATE TABLE parent_child_relationships (
    id uuid PRIMARY KEY,
    parent_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    child_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    type varchar(16) NOT NULL CHECK (type IN ('BIOLOGICAL', 'ADOPTIVE', 'STEP')),
    created_at timestamptz NOT NULL,
    CHECK (parent_id <> child_id),
    UNIQUE (parent_id, child_id, type)
);

CREATE INDEX parent_child_relationships_child_idx ON parent_child_relationships (child_id);

CREATE TABLE partnerships (
    id uuid PRIMARY KEY,
    person1_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    person2_id uuid NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    type varchar(16) NOT NULL CHECK (type IN ('MARRIAGE', 'PARTNERSHIP')),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED', 'UNKNOWN')),
    start_date date,
    start_date_precision varchar(16),
    end_date date,
    end_date_precision varchar(16),
    CHECK (person1_id < person2_id),
    CHECK (
        (start_date IS NULL AND start_date_precision IS NULL) OR
        (start_date IS NOT NULL AND start_date_precision IS NOT NULL
            AND start_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND CASE start_date_precision
                WHEN 'YEAR' THEN EXTRACT(MONTH FROM start_date) = 1 AND EXTRACT(DAY FROM start_date) = 1
                WHEN 'MONTH' THEN EXTRACT(DAY FROM start_date) = 1
                WHEN 'DAY' THEN true
                ELSE false
            END)
    ),
    CHECK (
        (end_date IS NULL AND end_date_precision IS NULL) OR
        (end_date IS NOT NULL AND end_date_precision IS NOT NULL
            AND end_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND CASE end_date_precision
                WHEN 'YEAR' THEN EXTRACT(MONTH FROM end_date) = 1 AND EXTRACT(DAY FROM end_date) = 1
                WHEN 'MONTH' THEN EXTRACT(DAY FROM end_date) = 1
                WHEN 'DAY' THEN true
                ELSE false
            END)
    )
);

CREATE INDEX partnerships_pair_idx ON partnerships (person1_id, person2_id);
CREATE INDEX partnerships_second_person_idx ON partnerships (person2_id);

INSERT INTO partnerships (id, person1_id, person2_id, type, status)
SELECT gen_random_uuid(), pair.person1_id, pair.person2_id, 'MARRIAGE', 'UNKNOWN'
FROM (
    SELECT DISTINCT LEAST(id, spouse_id) AS person1_id, GREATEST(id, spouse_id) AS person2_id
    FROM persons WHERE spouse_id IS NOT NULL
) pair;

DROP TABLE person_parentage;

ALTER TABLE persons
    DROP COLUMN is_root,
    DROP COLUMN generation,
    DROP COLUMN relationship,
    DROP COLUMN spouse_id;
