package com.moveinsync.roster.exception;

/**
 * Thrown when reconciliation detects that the fraction of missing employees
 * exceeds the configured safety threshold.
 */
public class SafetyBrakeException extends RuntimeException {

    private final double missingPercentage;
    private final double threshold;
    private final long totalInStore;
    private final long missingCount;

    public SafetyBrakeException(double missingPercentage, double threshold, long totalInStore, long missingCount) {
        super(String.format("Safety brake triggered: %.2f%% missing (threshold: %.2f%%). %d of %d active employees missing from export",
                missingPercentage * 100, threshold * 100, missingCount, totalInStore));
        this.missingPercentage = missingPercentage;
        this.threshold = threshold;
        this.totalInStore = totalInStore;
        this.missingCount = missingCount;
    }

    public double getMissingPercentage() {
        return missingPercentage;
    }

    public double getThreshold() {
        return threshold;
    }

    public long getTotalInStore() {
        return totalInStore;
    }

    public long getMissingCount() {
        return missingCount;
    }
}

