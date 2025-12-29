package iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard

/**
 * Leaderboard row model
 *
 * Backend: completeTimeSeconds (smaller = better)
 * Some code may use: completionTimeSeconds
 */
data class LeaderboardRow(
    val username: String,
    val completeTimeSeconds: Int,
    val completeAt: String = ""
) {
    // Compatibility alias (some code uses this name)
    val completionTimeSeconds: Int get() = completeTimeSeconds

    // Keep adapter API stable (your adapter calls these)
    fun displayName(): String = if (username.isBlank()) "unknown" else username
    fun displaySeconds(): Int = completeTimeSeconds
}
