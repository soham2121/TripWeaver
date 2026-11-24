-- Google Places is out of scope (decision D39). Remove the two objects created for it. Both were empty
-- when this ran (checked on Supabase and locally), and no code reads them. Approved by the owner (Q7).
DROP TABLE external_place_references;
ALTER TABLE places DROP COLUMN google_place_id;
