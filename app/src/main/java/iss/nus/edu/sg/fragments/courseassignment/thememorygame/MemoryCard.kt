
package iss.nus.edu.sg.fragments.courseassignment.thememorygame

data class MemoryCard(
    val id: Int,
    val contentSource: Any,
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false
)
