-- Compact evidence of each algorithm execution (plan sections 10.5 and 12).
CREATE TABLE algorithm_runs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    context VARCHAR(40) NOT NULL,
    trip_id UUID,
    algorithm VARCHAR(60) NOT NULL,
    algorithm_version VARCHAR(20) NOT NULL,
    input_size INTEGER NOT NULL,
    seed BIGINT,
    duration_micros BIGINT,
    summary_json TEXT NOT NULL
);
CREATE INDEX ix_algorithm_runs_created ON algorithm_runs (created_at);
CREATE INDEX ix_algorithm_runs_trip ON algorithm_runs (trip_id);
