package com.tripweaver.place;

/** Sourced opening hours of a place (OSM syntax) and their confidence label. */
public record PlaceHours(String raw, String confidence) {}
