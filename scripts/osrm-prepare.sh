#!/usr/bin/env sh
# Preprocess the Geofabrik western-zone extract for OSRM (car profile, Contraction Hierarchies).
# CH answers the many-to-many /table requests the planner makes about 10x faster than MLD here.
# Input:  data/raw/western-zone-261122.osm.pbf (see docs/DATA_SOURCES.md)
# Output: data/osrm/western-zone.osrm* (git-ignored), served by `docker compose --profile routing up osrm`.
set -eu
IMAGE=ghcr.io/project-osrm/osrm-backend:v6.0.0
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/data/osrm"
cp "$ROOT/data/raw/western-zone-261122.osm.pbf" "$ROOT/data/osrm/western-zone.osm.pbf"
docker run --rm -v "$ROOT/data/osrm:/data" "$IMAGE" osrm-extract -p /opt/car.lua /data/western-zone.osm.pbf
docker run --rm -v "$ROOT/data/osrm:/data" "$IMAGE" osrm-contract /data/western-zone.osrm
rm "$ROOT/data/osrm/western-zone.osm.pbf"
