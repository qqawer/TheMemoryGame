package iss.nus.edu.sg.fragments.courseassignment.thememorygame.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fetch_history")
data class FetchHistory(
    @PrimaryKey val url: String,
    val allImagePaths: String,      // 20张图片的本地路径，逗号分隔
    val selectedImagePaths: String, // 6张选中的图片路径，逗号分隔
    val timestamp: Long = System.currentTimeMillis()
)
