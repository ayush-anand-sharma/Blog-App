package com.ayushcodes.blogapp.repository // Defines the package for the UserRepository class.

import android.net.Uri // Imports the Uri class for handling resource identifiers.
import com.google.firebase.auth.FirebaseAuth // Imports the FirebaseAuth class for user authentication.
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase // Imports the FirebaseDatabase class for interacting with the Realtime Database.
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage // Imports the FirebaseStorage class for file storage.
import kotlinx.coroutines.tasks.await // Imports the await extension function to use coroutines with Tasks.
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserRepository { // A repository class to handle user data.

    private val auth = FirebaseAuth.getInstance() // Get the Firebase authentication instance.
    private val database = FirebaseDatabase.getInstance().reference // Get a reference to the root of the Firebase Realtime Database.
    private val storage = FirebaseStorage.getInstance().reference // Get a reference to the root of Firebase Storage.

    suspend fun saveUserProfile(
        userId: String, // The ID of the user.
        name: String, // The name of the user.
        email: String, // The email of the user.
        profileImageUrl: String // The URL of the user's profile image.
    ) {
        val userRef = database.child("users").child(userId) // Get a reference to the user's data in the database.
        val snapshot = userRef.get().await() // Asynchronously get the current data.

        if (snapshot.exists()) { // If the user already exists in the database.
            val updates = mutableMapOf<String, Any>() // Create a map to hold the data to be updated.
            val currentName = snapshot.child("name").getValue(String::class.java) // Get the current name from the database.
            val currentProfileImage = snapshot.child("profileImage").getValue(String::class.java) // Get the current profile image from the database.

            if (currentName.isNullOrEmpty()) { // If the name is missing in the database.
                updates["name"] = name // Update the name with the one from the Google account.
            }

            if (currentProfileImage.isNullOrEmpty()) { // If the profile image is missing.
                updates["profileImage"] = profileImageUrl // Update the profile image with the one from the Google account.
            }
            userRef.updateChildren(updates).await() // Perform the update only if there are changes.
        } else { // If the user is new.
            val creationTimestamp = auth.currentUser?.metadata?.creationTimestamp ?: System.currentTimeMillis() // Get the creation timestamp or the current time.
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp)) // Format the date.
            val userProfile = hashMapOf( // Create a new user profile.
                "name" to name,
                "email" to email,
                "profileImage" to profileImageUrl,
                "creationDate" to formattedDate
            )
            userRef.setValue(userProfile).await() // Set the new user's data in the database.
        }
    }

    fun updateUserProfile(userId: String, newName: String?, newImageUri: Uri?, oldImageUrl: String?, onResult: (Boolean, String) -> Unit) { // A function to update the user's profile.
        if (newImageUri != null) { // Check if a new image has been selected.
            val storageRef = storage.child("profile_images").child("${userId}_${System.currentTimeMillis()}.jpg") // Create a reference in Firebase Storage for the new image.
            storageRef.putFile(newImageUri).addOnSuccessListener { // Upload the new image and add a success listener.
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl -> // On successful upload, get the download URL for the image.
                    val newImageUrl = downloadUrl.toString() // Get the new image URL.
                    if (!oldImageUrl.isNullOrEmpty()) { // Check if there is an old image URL.
                        try { // Add a try-catch block to prevent crashes if the URL is invalid.
                            FirebaseStorage.getInstance().getReferenceFromUrl(oldImageUrl).delete() // Delete the old profile picture from Firebase Storage.
                        } catch (e: Exception) { // Catch any exceptions that occur.
                            // Log the error or handle it silently.
                        }
                    }
                    updateDatabase(userId, newName, newImageUrl, onResult) // Update the database with the new information.
                }
            }.addOnFailureListener { // Add a failure listener for the upload task.
                onResult(false, "Image upload failed. Please try again.") // Call the onResult callback with an error message.
            }
        } else if (newName != null) { // If only the name has changed.
            updateDatabase(userId, newName, null, onResult) // Update the database with only the new name.
        } else { // If there are no changes.
            onResult(true, "No changes to save.") // Call the onResult callback with a message.
        }
    }

    private fun updateDatabase(userId: String, newName: String?, newImageUrl: String?, onResult: (Boolean, String) -> Unit) { // A function to update the database.
        val updates = mutableMapOf<String, Any?>() // Create a mutable map to store the updates.
        if (newName != null) { // Check if a new name has been provided.
            updates["/users/$userId/name"] = newName // Add the new name to the updates map.
        }
        if (newImageUrl != null) { // Check if a new image URL has been provided.
            updates["/users/$userId/profileImage"] = newImageUrl // Add the new image URL to the updates map.
        }

        database.child("blogs").orderByChild("userId").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener { // Query for all blogs where the userId matches the current user's ID.
            override fun onDataChange(snapshot: DataSnapshot) { // Called when the data is retrieved.
                snapshot.children.forEach { blogSnapshot -> // Iterate through all the matching blog posts.
                    val blogId = blogSnapshot.key // Get the blog ID.
                    if (blogId != null) { // Check if the blog ID is not null.
                        if (newName != null) { // Check if a new name has been provided.
                            updates["/blogs/$blogId/fullName"] = newName // Update the full name in the main blogs node.
                            updates["/users/$userId/Blogs/$blogId/fullName"] = newName // Update the full name in the user's personal blogs node.
                        }
                        if (newImageUrl != null) { // Check if a new image URL has been provided.
                            updates["/blogs/$blogId/profileImage"] = newImageUrl // Update the profile image in the main blogs node.
                            updates["/users/$userId/Blogs/$blogId/profileImage"] = newImageUrl // Update the profile image in the user's personal blogs node.
                        }
                    }
                }
                database.updateChildren(updates).addOnCompleteListener { task -> // Update the database with the new information.
                    if (task.isSuccessful) { // Check if the update was successful.
                        onResult(true, "Profile updated successfully") // Call the onResult callback with a success message.
                    } else { // If the update failed.
                        onResult(false, "Failed to update profile. Please try again.") // Call the onResult callback with an error message.
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) { // Called if the query is cancelled or fails.
                onResult(false, "Failed to update profile. Please try again.") // Call the onResult callback with an error message.
            }
        })
    }
}
