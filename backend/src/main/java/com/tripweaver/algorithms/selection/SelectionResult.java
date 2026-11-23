package com.tripweaver.algorithms.selection;

import java.util.List;

/**
 * Output of a top-K selection.
 *
 * @param selected top K items, best first
 * @param threshold score of the K-th selected item, NaN when K = 0
 * @param seed random seed used for pivot choice
 * @param tiePolicy human-readable tie rule
 * @param inputSize number of candidates considered
 * @param partitionRounds number of partition passes performed (varies with the seed)
 */
public record SelectionResult<T>(
        List<T> selected, double threshold, long seed, String tiePolicy, int inputSize, int partitionRounds) {}
