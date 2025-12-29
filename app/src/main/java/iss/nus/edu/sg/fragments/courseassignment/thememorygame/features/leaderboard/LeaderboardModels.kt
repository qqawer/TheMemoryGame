package iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard

/**
 * Leaderboard row model
 *
 * Backend field: completeTimeSeconds
 * Some UI code may use: completionTimeSeconds
 * -> We support both to avoid breaking any existing code.
 */
data class LeaderboardRow(
    val username: String,
    val completeTimeSeconds: Int,
    val completeAt: String = ""
) {
    // Alias for compatibility with other code
    val completionTimeSeconds: Int get() = completeTimeSeconds
}
