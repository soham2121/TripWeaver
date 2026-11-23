package com.tripweaver.planning;

/**
 * Workload-based pace (plan section 3.3). A pace limits the share of the day window spent on visits and travel,
 * sets transition buffers and meal length, caps stops and high-intensity activities, and picks which end of a
 * place's visit-duration range to use.
 */
public enum Pace {
    RELAXED(0.55, 20, 75, 4, 1),
    BALANCED(0.70, 15, 60, 6, 1),
    PACKED(0.85, 10, 45, 8, 2);

    private final double maxUtilisation;
    private final int bufferMinutes;
    private final int mealMinutes;
    private final int maxStops;
    private final int maxHighIntensity;

    Pace(double maxUtilisation, int bufferMinutes, int mealMinutes, int maxStops, int maxHighIntensity) {
        this.maxUtilisation = maxUtilisation;
        this.bufferMinutes = bufferMinutes;
        this.mealMinutes = mealMinutes;
        this.maxStops = maxStops;
        this.maxHighIntensity = maxHighIntensity;
    }

    /** Maximum (visit + travel minutes) / day-window minutes for optional stops. */
    public double maxUtilisation() {
        return maxUtilisation;
    }

    public int bufferMinutes() {
        return bufferMinutes;
    }

    public int mealMinutes() {
        return mealMinutes;
    }

    public int maxStops() {
        return maxStops;
    }

    public int maxHighIntensity() {
        return maxHighIntensity;
    }

    /** Relaxed uses the top of the duration range, packed the bottom, balanced the middle. */
    public int visitMinutes(int min, int max) {
        return switch (this) {
            case RELAXED -> max;
            case PACKED -> min;
            case BALANCED -> (int) Math.round((min + max) / 2.0);
        };
    }

    /** Label derived from the actual utilisation of a day. */
    public static Pace label(double utilisation) {
        if (utilisation <= RELAXED.maxUtilisation + 1e-9) return RELAXED;
        if (utilisation <= BALANCED.maxUtilisation + 1e-9) return BALANCED;
        return PACKED;
    }
}
