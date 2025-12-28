package com.ayushcodes.blogapp.model // Defines the package name for this file

// This data class represents a user's profile data.
data class UserData( // Defines the data class
    // The user's full name.
    val name: String? = null, // Property for name, default null
    // The user's email address.
    val email: String? = null, // Property for email, default null
    // The user's password.
    val password: String? = null, // Property for password, default null
    // The URL of the user's profile image.
    val profileImage: String? = null, // Property for profile image URL, default null
    // The date the user created their account.
    val creationDate: String? = null // Property for creation date, default null
)
