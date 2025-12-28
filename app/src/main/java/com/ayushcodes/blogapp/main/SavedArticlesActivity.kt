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
import com.ayushcodes.blogapp.databinding.ActivitySavedArticlesBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.model.BlogItemModel // Imports the data model for blog items
import com.ayushcodes.blogapp.repository.BlogRepository // Imports the repository for data operations
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data from Firebase
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling database errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for listening to data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Activity responsible for displaying a list of blog articles saved by the user.
class SavedArticlesActivity : AppCompatActivity() { // Defines the SavedArticlesActivity class inheriting from AppCompatActivity

    // Lazily initialize view binding for the activity layout
    private lateinit var binding: ActivitySavedArticlesBinding // Declares a variable for view binding
    
    // Firebase Database instance to interact with Realtime Database
    private lateinit var database: FirebaseDatabase // Declares a variable for the Firebase Database instance
    
    // Firebase Authentication instance to manage user authentication
    private lateinit var auth: FirebaseAuth // Declares a variable for the Firebase Authentication instance
    
    // Adapter for the RecyclerView to display blog items
    private lateinit var blogAdapter: BlogAdapter // Declares a variable for the BlogAdapter

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method to initialize the activity
        super.onCreate(savedInstanceState) // Calls the superclass implementation of onCreate
        binding = ActivitySavedArticlesBinding.inflate(layoutInflater) // Inflates the layout using view binding
        setContentView(binding.root) // Sets the content view to the root of the binding

        // Initialize Firebase instances
        database = FirebaseDatabase.getInstance() // Gets the instance of FirebaseDatabase
        auth = FirebaseAuth.getInstance() // Gets the instance of FirebaseAuth

        // Set click listener for the back button to close the current activity
        binding.backButton.setOnClickListener { // Sets an OnClickListener on the back button
            finish() // Finishes the current activity to return to the previous screen
        }

        // Initialize the RecyclerView
        setupRecyclerView() // Calls the method to set up the RecyclerView
        observeInteractionState() // Calls the method to observe state changes

        // Fetch saved blogs from Firebase if network is available
        if (isNetworkAvailable()) { // Checks if the network is available
            fetchSavedBlogs() // Fetches saved blogs from the database
        } else { // Executed if the network is not available
            showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message indicating no internet connection
        }
    }

    // Configures the RecyclerView with the BlogAdapter and LayoutManager
    private fun setupRecyclerView() { // Defines a method to set up the RecyclerView
        blogAdapter = BlogAdapter( // Initializes the BlogAdapter with callback functions
            onReadMoreClick = { blogItem -> // Callback for when "Read More" is clicked
                // Handle clicks on the "Read More" button, navigating to the ReadMore activity
                if (isNetworkAvailable()) { // Checks if the network is available
                    val intent = Intent(this, ReadMore::class.java) // Creates an Intent to start the ReadMore activity
                    intent.putExtra("blogItem", blogItem) // Puts the blog item data into the Intent
                    startActivity(intent) // Starts the ReadMore activity
                } else { // Executed if the network is not available
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message
                }
            },
            onLikeClick = { blogItem -> // Callback for when the "Like" button is clicked
                if (auth.currentUser == null) { // Checks if the user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows a toast asking the user to login
                } else { // Executed if the user is logged in
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles the like status of the blog item
                }
            },
            onSaveClick = { blogItem -> // Callback for when the "Save" button is clicked
                if (auth.currentUser == null) { // Checks if the user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows a toast asking the user to login
                } else { // Executed if the user is logged in
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles the save status of the blog item
                }
            }
        )
        
        binding.savedArticlesRecyclerView.apply { // Applies configuration to the RecyclerView
            layoutManager = LinearLayoutManager(this@SavedArticlesActivity) // Sets the LayoutManager to LinearLayoutManager
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

    // Retrieves the current user's saved blogs from the Firebase Realtime Database
    private fun fetchSavedBlogs() { // Defines a method to fetch saved blogs
        // Show progress bar while loading data
        binding.progressBar.visibility = View.VISIBLE // Sets the progress bar to visible
        val userId = auth.currentUser?.uid ?: return // Gets the current user ID, returning if null
        val savedBlogsRef = database.reference.child("users").child(userId).child("savedBlogs") // References the "savedBlogs" node for the user

        // Attach a listener to get updates when saved blogs change
        savedBlogsRef.addValueEventListener(object : ValueEventListener { // Adds a ValueEventListener to the reference
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes at the location
                val savedBlogItems = mutableListOf<BlogItemModel>() // Creates a mutable list to hold blog items
                // Iterate through the snapshot and add each blog item to the list
                for (blogSnapshot in snapshot.children) { // Iterates through each child in the snapshot
                    val blogItem = blogSnapshot.getValue(BlogItemModel::class.java) // deserializes the snapshot into a BlogItemModel
                    if (blogItem != null) { // Checks if the deserialized item is not null
                        savedBlogItems.add(blogItem) // Adds the blog item to the list
                    }
                }
                savedBlogItems.reverse() // Display the most recently saved items first // Reverses the list to show newest first
                
                BlogRepository.initializeState(savedBlogItems) // Initializes the repository state with the fetched items
                blogAdapter.submitList(savedBlogItems) // Submits the list to the adapter
                
                binding.progressBar.visibility = View.GONE // Hide the progress bar // Hides the progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called when the listener is cancelled
                binding.progressBar.visibility = View.GONE // Hide the progress bar // Hides the progress bar
                showToast("Failed to load saved articles: ${error.message}", FancyToast.ERROR) // Shows a toast with the error message
            }
        })
    }

    // Checks for active network connectivity
    private fun isNetworkAvailable(): Boolean { // Defines a method to check network availability
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager service
        val network = connectivityManager.activeNetwork ?: return false // Gets the active network, returns false if none
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets network capabilities, returns false if none
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks if Wi-Fi is available
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks if Cellular is available
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks if Ethernet is available
    }

    // Helper function to show a custom toast message
    private fun showToast(message: String, type: Int) { // Defines a helper method to show toasts
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast
    }
}
