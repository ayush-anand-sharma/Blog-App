package com.ayushcodes.blogapp.model // Defines the package name for this file

// Handles global like/save state synchronization across all screens
data class PostInteractionState( // Defines the data class
    val blogId: String, // Property for blog ID
    val isLiked: Boolean = false, // Property for liked status, default false
    val isSaved: Boolean = false, // Property for saved status, default false
    val likeCount: Int = 0, // Property for like count, default 0
    val isLikeToggling: Boolean = false // Prevents UI flicker during rapid updates // Property for toggling state, default false
)
