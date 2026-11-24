ALTER TABLE persons
    ADD COLUMN biography text CHECK (char_length(biography) <= 100000),
    ADD COLUMN death_country varchar(255),
    ADD COLUMN death_region varchar(255),
    ADD COLUMN death_city varchar(255),
    ADD COLUMN occupation varchar(255),
    ADD COLUMN burial_place varchar(255);

CREATE TABLE person_alternative_names (
    id uuid PRIMARY KEY,
    person_id uuid NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL CHECK (btrim(name) <> ''),
    type varchar(32) NOT NULL CHECK (type IN ('FORMER_NAME', 'NICKNAME', 'ALTERNATIVE_SPELLING', 'OTHER'))
);

CREATE UNIQUE INDEX person_alternative_names_unique
    ON person_alternative_names (person_id, type, lower(name));

CREATE TABLE person_photos (
    id uuid PRIMARY KEY,
    person_id uuid NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    original_file_name varchar(255) NOT NULL,
    storage_key varchar(100) NOT NULL UNIQUE,
    thumbnail_storage_key varchar(100) NOT NULL UNIQUE,
    content_type varchar(16) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    sha256 varchar(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    caption varchar(1000),
    taken_date date,
    taken_date_precision varchar(16),
    is_primary boolean NOT NULL,
    created_at timestamptz NOT NULL,
    CHECK (
        (taken_date IS NULL AND taken_date_precision IS NULL) OR
        (taken_date IS NOT NULL AND taken_date_precision IS NOT NULL
            AND taken_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'
            AND CASE taken_date_precision
                WHEN 'YEAR' THEN EXTRACT(MONTH FROM taken_date) = 1 AND EXTRACT(DAY FROM taken_date) = 1
                WHEN 'MONTH' THEN EXTRACT(DAY FROM taken_date) = 1
                WHEN 'DAY' THEN true
                ELSE false
            END)
    )
);

CREATE INDEX person_photos_by_person ON person_photos (person_id);
CREATE UNIQUE INDEX person_photos_one_primary ON person_photos (person_id) WHERE is_primary;

-- Durable queue for individual photo deletion and deletion by the person's foreign key.
CREATE TABLE photo_file_cleanup (
    photo_id uuid PRIMARY KEY,
    queued_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION queue_photo_file_cleanup() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    INSERT INTO photo_file_cleanup (photo_id) VALUES (OLD.id) ON CONFLICT DO NOTHING;
    RETURN OLD;
END;
$$;

CREATE TRIGGER person_photos_queue_cleanup AFTER DELETE ON person_photos
    FOR EACH ROW EXECUTE FUNCTION queue_photo_file_cleanup();
