package com.ayushcodes.blogapp.main // Defines the package name for this file

import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Bundle // Imports Bundle to pass data between components
import android.view.View // Imports View class for UI elements
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as base class
import com.ayushcodes.blogapp.R // Imports R class for resources
import com.ayushcodes.blogapp.databinding.ActivityAddArticleBinding // Imports generated binding class
import com.ayushcodes.blogapp.model.BlogItemModel // Imports BlogItemModel data class
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for custom toasts
import java.text.SimpleDateFormat // Imports SimpleDateFormat for formatting dates
import java.util.Date // Imports Date class
import java.util.Locale // Imports Locale class

// Activity responsible for creating and adding new blog articles.
class AddArticle : AppCompatActivity() { // Defines AddArticle class inheriting from AppCompatActivity

    // Lazily initialize view binding for the activity layout
    private val binding: ActivityAddArticleBinding by lazy { // Declares binding with lazy initialization
        ActivityAddArticleBinding.inflate(layoutInflater) // Inflates the layout
    }

    // Firebase Authentication instance to manage user authentication
    private val auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
    
    // Firebase Database instance to interact with Realtime Database
    private val database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        enableEdgeToEdge() // Enables edge-to-edge display
        setContentView(binding.root) // Sets content view using binding

        // Set click listener for the back button to close the current activity
        binding.backButton.setOnClickListener { finish() } // Sets OnClickListener to finish activity

        // Set click listener for the add blog button, checking for network availability
        binding.addBlogButton.setOnClickListener { // Sets OnClickListener for add blog button
            if (isNetworkAvailable()) { // Checks if network is available
                addBlog() // Calls addBlog function
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.ERROR) // Shows error toast
            }
        }
    }

    // Handles the logic for adding a new blog post to Firebase
    private fun addBlog() { // Defines function to add blog
        // Get title and description from input fields
        val title = binding.blogTitle.text.toString().trim() // Gets trimmed title text
        val description = binding.blogDescription.text.toString().trim() // Gets trimmed description text

        // Validate input fields
        if (title.isEmpty() || description.isEmpty()) { // Checks if title or description is empty
            showToast("Please fill all the fields", FancyToast.WARNING) // Shows warning toast
            return // Returns from function
        }

        // Check if the user is authenticated
        val user = auth.currentUser // Gets current user
        if (user == null) { // Checks if user is null
            showToast("User not logged in!", FancyToast.ERROR) // Shows error toast
            return // Returns from function
        }
        
        // Show progress bar immediately
        binding.progressBar.visibility = View.VISIBLE // Shows progress bar
        binding.addBlogButton.isEnabled = false // Disables add blog button

        val userId = user.uid // Gets user ID
        // Fetch the correct profile image from Database (Source of Truth) to support both Email and Google users
        database.getReference("users").child(userId).child("profileImage") // References profileImage node
            .addListenerForSingleValueEvent(object : ValueEventListener { // Adds single value event listener
                override fun onDataChange(snapshot: DataSnapshot) { // Called when data is received
                    // Get image from DB, fallback to Auth photo, fallback to empty string
                    val dbProfileImage = snapshot.getValue(String::class.java) // Gets profile image from snapshot
                    val profileImage = if (!dbProfileImage.isNullOrEmpty()) { // Checks if DB image is valid
                        dbProfileImage // Uses DB image
                    } else { // Executed if DB image is invalid
                        user.photoUrl?.toString() ?: "" // Uses auth photo URL or empty string
                    }

                    proceedWithUpload(user.uid, user.displayName, profileImage, title, description) // Calls proceedWithUpload
                }

                override fun onCancelled(error: DatabaseError) { // Called if database error occurs
                    // Fallback to Auth photo if DB read fails (rare)
                    val profileImage = user.photoUrl?.toString() ?: "" // Uses auth photo URL or empty string
                    proceedWithUpload(user.uid, user.displayName, profileImage, title, description) // Calls proceedWithUpload
                }
            })
    }

    // Uploads the blog data to Firebase Database
    private fun proceedWithUpload(userId: String, displayName: String?, profileImage: String, title: String, description: String) { // Defines function to upload data
        val fullName = displayName ?: "Anonymous" // Gets display name or default
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) // Formats current date

        // Generate a new key for the blog post
        val blogsRef = database.getReference("blogs") // References blogs node
        val newBlogKey = blogsRef.push().key // Generates new key
        if (newBlogKey == null) { // Checks if key generation failed
            binding.progressBar.visibility = View.GONE // Hides progress bar
            binding.addBlogButton.isEnabled = true // Enables add blog button
            showToast("Failed to create a new blog post", FancyToast.ERROR) // Shows error toast
            return // Returns from function
        }

        // Create a BlogItemModel object
        val blogItem = BlogItemModel( // Creates BlogItemModel instance
            blogId = newBlogKey, // Sets blog ID
            userId = userId, // Sets user ID
            heading = title, // Sets title
            fullName = fullName, // Sets full name
            date = currentDate, // Sets date
            blogPost = description, // Sets description
            likeCount = 0, // Sets like count
            profileImage = profileImage // Sets profile image
        )

        // Define paths for simultaneous updates in the database
        val childUpdates = hashMapOf<String, Any>( // Creates map for updates
            "/blogs/$newBlogKey" to blogItem, // Update path for global blogs
            "/users/$userId/Blogs/$newBlogKey" to blogItem // Update path for user's blogs
        )

        // Fire and forget upload
        database.reference.updateChildren(childUpdates) // Updates children in database
            .addOnFailureListener { // Adds failure listener
                // Logs or silent failure handling if needed
            }

        // Optimistic UI: Assume success and close immediately
        showToast("Blog uploading...", FancyToast.INFO) // Shows info toast
        
        val intent = Intent(this@AddArticle, HomePage::class.java) // Creates intent for HomePage
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) // Adds flags to clear stack
        startActivity(intent) // Starts HomePage activity
        finish() // Finishes current activity
    }

    // Checks for network connectivity
    private fun isNetworkAvailable(): Boolean { // Defines function to check network
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager // Gets ConnectivityManager
        return connectivityManager?.activeNetwork?.let { // Checks active network
            val capabilities = connectivityManager.getNetworkCapabilities(it) // Gets network capabilities
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || // Checks for WiFi
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true || // Checks for Cellular
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true // Checks for Ethernet
        } ?: false // Returns false if null
    }

    // Helper function to show a custom toast message
    private fun showToast(message: String, type: Int) { // Defines function to show toast
        FancyToast.makeText( // Creates FancyToast
            this, // Sets context
            message, // Sets message
            FancyToast.LENGTH_SHORT, // Sets duration
            type, // Sets type
            R.mipmap.blog_app_icon_round, // Sets icon
            false // Sets android icon parameter
        ).show() // Shows the toast
    }
}
