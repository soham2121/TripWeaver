-- Baseline marker. Domain tables arrive in later migrations.
CREATE TABLE schema_info (
    id INTEGER PRIMARY KEY,
    note VARCHAR(200) NOT NULL
);
INSERT INTO schema_info (id, note) VALUES (1, 'TripWeaver baseline');
