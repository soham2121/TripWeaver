-- Catalogue integrity, missing foreign-key indexes and external provider references.
-- Portable SQL for PostgreSQL and H2 (tests). PostgreSQL-only parts (PostGIS, RLS) are in db/vendor/postgresql.

-- Data confidence labels (IMPLEMENTATION.md 0.1 rule 6).
ALTER TABLE place_source_records ADD CONSTRAINT ck_source_confidence
    CHECK (confidence IN ('VERIFIED', 'PROVIDER_SOURCED', 'CURATED_ESTIMATE', 'USER_ENTERED', 'SYNTHETIC', 'UNKNOWN'));
ALTER TABLE place_cost_estimates ADD CONSTRAINT ck_cost_confidence
    CHECK (confidence IN ('VERIFIED', 'PROVIDER_SOURCED', 'CURATED_ESTIMATE', 'USER_ENTERED', 'SYNTHETIC', 'UNKNOWN'));
ALTER TABLE place_opening_hours ADD CONSTRAINT ck_hours_confidence
    CHECK (confidence IN ('VERIFIED', 'PROVIDER_SOURCED', 'CURATED_ESTIMATE', 'USER_ENTERED', 'SYNTHETIC', 'UNKNOWN'));
ALTER TABLE places ADD CONSTRAINT ck_visit_confidence
    CHECK (visit_minutes_confidence IN ('VERIFIED', 'PROVIDER_SOURCED', 'CURATED_ESTIMATE', 'USER_ENTERED', 'SYNTHETIC', 'UNKNOWN'));

-- Coordinates, durations and costs must be sane. An unknown cost is a missing row, never a zero row.
ALTER TABLE places ADD CONSTRAINT ck_place_lat CHECK (latitude BETWEEN -90 AND 90);
ALTER TABLE places ADD CONSTRAINT ck_place_lon CHECK (longitude BETWEEN -180 AND 180);
ALTER TABLE places ADD CONSTRAINT ck_place_visit
    CHECK (visit_minutes_min IS NULL OR visit_minutes_max IS NULL
           OR (visit_minutes_min > 0 AND visit_minutes_min <= visit_minutes_max));
ALTER TABLE places ADD CONSTRAINT ck_place_osm
    CHECK ((osm_type IS NULL AND osm_id IS NULL) OR (osm_type IN ('node', 'way', 'relation') AND osm_id IS NOT NULL));
ALTER TABLE place_cost_estimates ADD CONSTRAINT ck_cost_range CHECK (amount_min >= 0 AND amount_min <= amount_max);
ALTER TABLE place_cost_estimates ADD CONSTRAINT ck_cost_currency CHECK (LENGTH(currency) = 3);
ALTER TABLE destinations ADD CONSTRAINT ck_destination_bbox CHECK (min_lat <= max_lat AND min_lon <= max_lon);

-- Indexes on foreign keys that had none (joins and ON DELETE checks).
CREATE INDEX ix_regions_destination ON regions (destination_id);
CREATE INDEX ix_places_region ON places (region_id);
CREATE INDEX ix_places_import_batch ON places (import_batch_id);
CREATE INDEX ix_import_batches_destination ON place_import_batches (destination_id, imported_at);
CREATE INDEX ix_share_links_created_by ON trip_share_links (created_by);
CREATE INDEX ix_suggestions_place ON activity_suggestions (place_id);
CREATE INDEX ix_suggestions_user ON activity_suggestions (suggested_by);
CREATE INDEX ix_votes_user ON activity_votes (user_id);
CREATE INDEX ix_expenses_paid_by ON trip_expenses (paid_by);
CREATE INDEX ix_change_history_user ON trip_change_history (user_id);

-- Identifiers of the same place at an external provider. Only identifiers are stored: Google Place IDs
-- may be kept indefinitely under the Google Maps Platform terms; ratings, reviews, hours, photos and
-- other Places content are never written to the database.
CREATE TABLE external_place_references (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    match_method VARCHAR(40) NOT NULL,
    matched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_external_place UNIQUE (place_id, provider),
    CONSTRAINT ck_external_provider CHECK (provider IN ('GOOGLE_PLACES'))
);
CREATE INDEX ix_external_id ON external_place_references (provider, external_id);
