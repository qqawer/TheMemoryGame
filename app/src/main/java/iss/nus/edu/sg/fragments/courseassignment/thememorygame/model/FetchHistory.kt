package iss.nus.edu.sg.fragments.courseassignment.thememorygame.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fetch_history")
data class FetchHistory(
    @PrimaryKey val url: String,
    val allImagePaths: String,      // Local paths of 20 images, separated by commas
    val selectedImagePaths: String, // Local paths of 6 selected images, separated by commas
    val timestamp: Long = System.currentTimeMillis()
)
