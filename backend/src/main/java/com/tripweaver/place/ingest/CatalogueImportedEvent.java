package com.tripweaver.place.ingest;

/** Published after a catalogue import commits. */
public record CatalogueImportedEvent(String destinationSlug) {}
