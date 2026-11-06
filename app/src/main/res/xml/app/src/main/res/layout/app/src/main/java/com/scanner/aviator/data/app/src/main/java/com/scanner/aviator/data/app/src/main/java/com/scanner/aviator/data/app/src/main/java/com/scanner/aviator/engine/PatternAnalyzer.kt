package com.scanner.aviator.engine

import com.scanner.aviator.data.RoundEntity

data class PatternMatch(
    val matchedSequence: List<Double>,
    val occurrences: Int,
    val nextMultiplierHistory: List<Double>
)

object PatternAnalyzer {
    private const val SEQUENCE_LEN = 3

    fun analyzeRecentSequence(allRounds: List<RoundEntity>): PatternMatch? {
        if (allRounds.size < SEQUENCE_LEN * 2) return null

        val recentWindow = allRounds.take(SEQUENCE_LEN).map { it.multiplier }.reversed()
        val historyChronological = allRounds.reversed().map { it.multiplier }

        val searchTarget = recentWindow.map { bucket(it) }
        var matchCount = 0
        val outcomesFollowingPattern = mutableListOf<Double>()

        for (i in 0 until (historyChronological.size - SEQUENCE_LEN - 1)) {
            val subList = historyChronological.subList(i, i + SEQUENCE_LEN).map { bucket(it) }
            if (subList == searchTarget) {
                matchCount++
                outcomesFollowingPattern.add(historyChronological[i + SEQUENCE_LEN])
            }
        }

        return if (matchCount > 0) {
            PatternMatch(recentWindow, matchCount, outcomesFollowingPattern)
        } else {
            null
        }
    }

    private fun bucket(value: Double): String {
        return when {
            value < 2.0 -> "L"
            value < 10.0 -> "M"
            else -> "H"
        }
    }
}
