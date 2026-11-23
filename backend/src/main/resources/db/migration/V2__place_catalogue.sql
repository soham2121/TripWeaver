-- Place catalogue (plan section 12). Portable SQL for PostgreSQL and H2 (tests).

CREATE TABLE destinations (
    id UUID PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    min_lat DOUBLE PRECISION NOT NULL,
    min_lon DOUBLE PRECISION NOT NULL,
    max_lat DOUBLE PRECISION NOT NULL,
    max_lon DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE regions (
    id UUID PRIMARY KEY,
    destination_id UUID NOT NULL REFERENCES destinations (id),
    slug VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lon DOUBLE PRECISION NOT NULL,
    CONSTRAINT uq_region_slug UNIQUE (destination_id, slug)
);

CREATE TABLE place_import_batches (
    id UUID PRIMARY KEY,
    destination_id UUID NOT NULL REFERENCES destinations (id),
    source VARCHAR(40) NOT NULL,
    source_file VARCHAR(255) NOT NULL,
    source_timestamp TIMESTAMP WITH TIME ZONE,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted INTEGER NOT NULL,
    rejected INTEGER NOT NULL,
    merged INTEGER NOT NULL,
    report_json TEXT NOT NULL
);

CREATE TABLE places (
    id UUID PRIMARY KEY,
    destination_id UUID NOT NULL REFERENCES destinations (id),
    region_id UUID REFERENCES regions (id),
    osm_type VARCHAR(10),
    osm_id BIGINT,
    google_place_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    neighbourhood VARCHAR(160),
    primary_category VARCHAR(40) NOT NULL,
    is_activity BOOLEAN NOT NULL,
    visit_minutes_min INTEGER,
    visit_minutes_max INTEGER,
    visit_minutes_basis VARCHAR(200),
    visit_minutes_confidence VARCHAR(30) NOT NULL,
    activity_intensity VARCHAR(20),
    best_time_of_day VARCHAR(40),
    seasonal_notes TEXT,
    seasonal_closed_months VARCHAR(40),
    website VARCHAR(500),
    osm_tags_json TEXT,
    data_quality_status VARCHAR(20) NOT NULL,
    missing_fields VARCHAR(200),
    is_active BOOLEAN NOT NULL,
    import_batch_id UUID REFERENCES place_import_batches (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_place_osm UNIQUE (osm_type, osm_id)
);
CREATE INDEX ix_places_destination ON places (destination_id, is_active);
CREATE INDEX ix_places_category ON places (primary_category);
CREATE INDEX ix_places_lat_lon ON places (latitude, longitude);

CREATE TABLE place_categories (
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    category VARCHAR(60) NOT NULL,
    PRIMARY KEY (place_id, category)
);

CREATE TABLE place_interest_tags (
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    tag VARCHAR(40) NOT NULL,
    PRIMARY KEY (place_id, tag)
);
CREATE INDEX ix_interest_tag ON place_interest_tags (tag);

CREATE TABLE place_accessibility_tags (
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    tag VARCHAR(40) NOT NULL,
    PRIMARY KEY (place_id, tag)
);

-- Field-level provenance: which source supplied which field, when, and how confident we are.
CREATE TABLE place_source_records (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    field_name VARCHAR(60) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_ref VARCHAR(200),
    source_url VARCHAR(500),
    confidence VARCHAR(30) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    note TEXT
);
CREATE INDEX ix_source_place ON place_source_records (place_id);

CREATE TABLE place_cost_estimates (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    amount_min NUMERIC(12, 2) NOT NULL,
    amount_max NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    basis VARCHAR(20) NOT NULL,
    confidence VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_url VARCHAR(500),
    verified_at TIMESTAMP WITH TIME ZONE,
    note TEXT
);
CREATE INDEX ix_cost_place ON place_cost_estimates (place_id);

-- Only sourced opening hours are stored (OSM opening_hours syntax).
CREATE TABLE place_opening_hours (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    raw VARCHAR(500) NOT NULL,
    confidence VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_url VARCHAR(500),
    verified_at TIMESTAMP WITH TIME ZONE,
    note TEXT
);
CREATE INDEX ix_hours_place ON place_opening_hours (place_id);
