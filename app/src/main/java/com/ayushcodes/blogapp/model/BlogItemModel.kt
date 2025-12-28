package com.ayushcodes.blogapp.model // Defines the package name for this file

import android.os.Parcelable // Imports Parcelable interface
import kotlinx.parcelize.Parcelize // Imports Parcelize annotation

// This data class represents a single blog post item.
@Parcelize // Annotates the class to automatically generate Parcelable implementation
data class BlogItemModel( // Defines the data class
    // The unique ID of the blog post.
    val blogId: String? = null, // Property for blog ID, default null
    // The ID of the user who created the blog post.
    val userId: String? = null, // Property for user ID, default null
    // The heading or title of the blog post.
    val heading: String? = null, // Property for heading, default null
    // The full name of the user who created the blog post.
    val fullName: String? = null, // Property for full name, default null
    // The date the blog post was created.
    val date: String? = null, // Property for date, default null
    // The main content of the blog post.
    val blogPost: String? = null, // Property for blog post content, default null
    // The number of likes the blog post has.
    var likeCount: Int = 0, // Property for like count, default 0
    // The URL of the user's profile image.
    val profileImage: String? = null, // Property for profile image URL, default null
    // A map of user IDs who have liked the blog post.
    var likes: MutableMap<String, Boolean>? = null // Property for likes map, default null
) : Parcelable // Implements Parcelable interface
