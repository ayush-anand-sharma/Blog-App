package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context // Imports Context class for accessing application environment
import android.content.Intent // Imports Intent class for launching activities
import android.net.ConnectivityManager // Imports ConnectivityManager for handling network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities for checking network capabilities
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.view.View // Imports View class for UI elements
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import androidx.lifecycle.lifecycleScope // Imports lifecycleScope for managing coroutines within the activity lifecycle
import androidx.recyclerview.widget.LinearLayoutManager // Imports LinearLayoutManager for arranging RecyclerView items
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.adapter.BlogAdapter // Imports the custom adapter for the blog list
import com.ayushcodes.blogapp.databinding.ActivityUserProfileBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.model.BlogItemModel // Imports the data model for blog items
import com.ayushcodes.blogapp.repository.BlogRepository // Imports the repository for data operations
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data from Firebase
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling database errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for listening to data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Activity to display a read-only profile view of another user.
class UserProfileActivity : AppCompatActivity() { // Defines the UserProfileActivity class inheriting from AppCompatActivity

    // Lazily initialize view binding for the activity layout
    private lateinit var binding: ActivityUserProfileBinding // Declares a variable for view binding
    
    // Firebase Database instance to fetch user data
    private lateinit var database: FirebaseDatabase // Declares a variable for the Firebase Database instance
    
    // Firebase Auth
    private lateinit var auth: FirebaseAuth // Declares a variable for the Firebase Authentication instance

    // Adapter reference
    private lateinit var blogAdapter: BlogAdapter // Declares a variable for the BlogAdapter

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method to initialize the activity
        super.onCreate(savedInstanceState) // Calls the superclass implementation of onCreate
        binding = ActivityUserProfileBinding.inflate(layoutInflater) // Inflates the layout using view binding
        setContentView(binding.root) // Sets the content view to the root of the binding

        // Initialize Firebase
        database = FirebaseDatabase.getInstance() // Gets the instance of FirebaseDatabase
        auth = FirebaseAuth.getInstance() // Gets the instance of FirebaseAuth

        // Set click listener for the back button to close the current activity
        binding.backButton.setOnClickListener { finish() } // Sets an OnClickListener on the back button to finish the activity

        // Retrieve the user ID from the intent extras
        val userId = intent.getStringExtra("userId") // Gets the "userId" string extra from the intent
        if (userId == null) { // Checks if the userId is null
            finish() // Exit if no valid userId is provided // Finishes the activity
            return // Returns from the method
        }

        // Initialize Recycler View
        setupRecyclerView() // Calls the method to set up the RecyclerView
        observeInteractionState() // Calls the method to observe state changes

        // Load user profile and blogs if network is available
        if (isNetworkAvailable()) { // Checks if the network is available
            loadUserProfile(userId) // Loads the user's profile information
            loadUserBlogs(userId) // Loads the user's blogs
        } else { // Executed if the network is not available
            showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message indicating no internet connection
        }
    }

    // Configures the RecyclerView and adapter
    private fun setupRecyclerView() { // Defines a method to set up the RecyclerView
        blogAdapter = BlogAdapter( // Initializes the BlogAdapter with callback functions
            onReadMoreClick = { blogItem -> // Callback for when "Read More" is clicked
                val intent = Intent(this@UserProfileActivity, ReadMore::class.java) // Creates an Intent to start the ReadMore activity
                intent.putExtra("blogItem", blogItem) // Puts the blog item data into the Intent
                startActivity(intent) // Starts the ReadMore activity
            },
            onLikeClick = { blogItem -> // Callback for when the "Like" button is clicked
                if (!isNetworkAvailable()) { // Checks if the network is available
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message
                } else if (auth.currentUser == null) { // Checks if the user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows a toast asking the user to login
                } else { // Executed if logged in and online
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles the like status of the blog item
                }
            },
            onSaveClick = { blogItem -> // Callback for when the "Save" button is clicked
                if (!isNetworkAvailable()) { // Checks if the network is available
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message
                } else if (auth.currentUser == null) { // Checks if the user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows a toast asking the user to login
                } else { // Executed if logged in and online
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles the save status of the blog item
                }
            }
        )
        
        binding.userBlogsRecyclerView.apply { // Applies configuration to the RecyclerView
            layoutManager = LinearLayoutManager(this@UserProfileActivity) // Sets the LayoutManager to LinearLayoutManager
            adapter = blogAdapter // Sets the adapter for the RecyclerView
        }
    }

    // Observes the global interaction state for UI updates
    private fun observeInteractionState() { // Defines a method to observe interaction state
        lifecycleScope.launch { // Launches a coroutine in the lifecycle scope
            BlogRepository.interactionState.collect { state -> // Collects updates from the interaction state flow
                blogAdapter.updateInteractionState(state) // Updates the adapter with the new interaction state
            }
        }
    }

    // Loads the user's profile information from Firebase Database
    private fun loadUserProfile(userId: String) { // Defines a method to load user profile
        // Show progress bar while loading data
        binding.progressBar.visibility = View.VISIBLE // Sets the progress bar to visible
        val userRef = database.reference.child("users").child(userId) // References the user node in the database
        // Retrieve data once from the database
        userRef.addListenerForSingleValueEvent(object : ValueEventListener { // Adds a single value event listener
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data is received
                // Extract user details from the snapshot
                val name = snapshot.child("name").getValue(String::class.java) // Gets the user's name
                val profileImage = snapshot.child("profileImage").getValue(String::class.java) // Gets the user's profile image URL
                val creationDate = snapshot.child("creationDate").getValue(String::class.java) // Gets the user's creation date

                // Populate UI with user details
                binding.userFullName.text = name // Sets the name TextView
                binding.memberSinceDate.text = creationDate // Sets the member since TextView
                if (profileImage != null && !isDestroyed) { // Checks if profile image is not null and activity is active
                    Glide.with(this@UserProfileActivity).load(profileImage).into(binding.userProfileImage) // Loads the profile image using Glide
                }
                binding.progressBar.visibility = View.GONE // Hide progress bar on success // Hides the progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called if the operation is cancelled
                binding.progressBar.visibility = View.GONE // Hide progress bar on failure // Hides the progress bar
                showToast("Failed to load user data. Please try again.", FancyToast.ERROR) // Shows an error toast
            }
        })
    }

    // Loads the list of blogs created by the user from Firebase Database
    private fun loadUserBlogs(userId: String) { // Defines a method to load user blogs
        // Show progress bar while loading data
        binding.progressBar.visibility = View.VISIBLE // Sets the progress bar to visible
        val userBlogsRef = database.reference.child("users").child(userId).child("Blogs") // References the user's blogs node
        // Retrieve data once from the database
        userBlogsRef.addListenerForSingleValueEvent(object : ValueEventListener { // Adds a single value event listener
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data is received
                val blogItems = mutableListOf<BlogItemModel>() // Creates a mutable list for blog items
                // Iterate through blog snapshots and add to list
                for (blogSnapshot in snapshot.children) { // Iterates through each child in the snapshot
                    val blogItem = blogSnapshot.getValue(BlogItemModel::class.java) // deserializes the snapshot into a BlogItemModel
                    if (blogItem != null) { // Checks if the deserialized item is not null
                        blogItems.add(blogItem) // Adds the blog item to the list
                    }
                }
                blogItems.reverse() // Reverse list to show newest blogs first // Reverses the list to show newest first
                
                // Initialize repository state for these blogs
                BlogRepository.initializeState(blogItems) // Initializes the repository state with the fetched items
                
                blogAdapter.submitList(blogItems) // Submits the list to the adapter
                binding.progressBar.visibility = View.GONE // Hide progress bar on success // Hides the progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called if the operation is cancelled
                binding.progressBar.visibility = View.GONE // Hide progress bar on failure // Hides the progress bar
                showToast("Failed to load blogs. Please try again.", FancyToast.ERROR) // Shows an error toast
            }
        })
    }

    // Checks for active network connection
    private fun isNetworkAvailable(): Boolean { // Defines a method to check network availability
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager service
        val network = connectivityManager.activeNetwork ?: return false // Gets the active network, returns false if none
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets network capabilities, returns false if none
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks if Wi-Fi is available
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks if Cellular is available
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks if Ethernet is available
    }

    // Helper function to display custom toast messages
    private fun showToast(message: String, type: Int) { // Defines a helper method to show toasts
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast
    }
}
