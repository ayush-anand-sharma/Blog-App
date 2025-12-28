package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.annotation.SuppressLint // Imports SuppressLint for suppressing warnings
import android.content.BroadcastReceiver // Imports BroadcastReceiver for handling system broadcasts
import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.content.IntentFilter // Imports IntentFilter for filtering broadcasts
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Bundle // Imports Bundle to pass data between components
import android.view.View // Imports View class for UI elements
import android.widget.SearchView // Imports SearchView for search functionality
import androidx.activity.OnBackPressedCallback // Imports OnBackPressedCallback for handling back presses
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as base class
import androidx.lifecycle.lifecycleScope // Imports lifecycleScope for managing coroutines
import androidx.recyclerview.widget.LinearLayoutManager // Imports LinearLayoutManager for arranging RecyclerView items
import cn.pedant.SweetAlert.SweetAlertDialog // Imports SweetAlertDialog for nice alerts
import com.ayushcodes.blogapp.R // Imports R class for resources
import com.ayushcodes.blogapp.adapter.BlogAdapter // Imports BlogAdapter for RecyclerView
import com.ayushcodes.blogapp.databinding.ActivityHomePageBinding // Imports generated binding class
import com.ayushcodes.blogapp.model.BlogItemModel // Imports BlogItemModel data class
import com.ayushcodes.blogapp.repository.BlogRepository // Imports BlogRepository for data operations
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling errors
import com.google.firebase.database.DatabaseReference // Imports DatabaseReference for accessing database paths
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for custom toasts
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Main activity of the app, displaying the feed of blog posts.
@Suppress("DEPRECATION") // Suppresses deprecation warnings for this class
class HomePage : AppCompatActivity() { // Defines HomePage class inheriting from AppCompatActivity

    // Lazily initialize view binding for the activity layout
    private val binding: ActivityHomePageBinding by lazy { // Declares binding with lazy initialization
        ActivityHomePageBinding.inflate(layoutInflater) // Inflates the layout
    }

    // Firebase Authentication instance for managing user login state
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    
    // Firebase Database instance for realtime data operations
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Adapter to manage and display blog items in the RecyclerView
    private lateinit var blogAdapter: BlogAdapter // Declares BlogAdapter instance

    // Firebase database reference for the "blogs" node
    private lateinit var blogsRef: DatabaseReference // Declares DatabaseReference for blogs
    
    // Listener for changes in the "blogs" node
    private var blogListener: ValueEventListener? = null // Declares ValueEventListener for blogs, nullable

    // Track if we were previously offline to show "Back Online" only when transitioning
    private var wasOffline = false // Flag to track offline state

    // List to store all loaded blogs for search functionality
    private val allBlogs = mutableListOf<BlogItemModel>() // Creates a list to hold all blog items

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        try { // Starts try block for persistence
            // Enable disk persistence for offline capabilities
            FirebaseDatabase.getInstance().setPersistenceEnabled(true) // Enables Firebase persistence
        } catch (e: Exception) { // Catches generic exception
            // Already enabled
        }
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Navigate to the user profile page when the profile card is clicked
        binding.cardView3.setOnClickListener { // Sets OnClickListener for profile card
            startActivity(Intent(this, ProfilePage::class.java)) // Starts ProfilePage activity
        }

        // Navigate to the saved articles page
        binding.saveArticleButton.setOnClickListener { // Sets OnClickListener for saved articles button
            startActivity(Intent(this, SavedArticlesActivity::class.java)) // Starts SavedArticlesActivity
        }

        // Initialize the RecyclerView and Firebase listeners
        setupRecyclerView() // Calls setupRecyclerView
        setupFirebaseListeners() // Calls setupFirebaseListeners
        observeInteractionState() // Calls observeInteractionState
        setupSearchListener() // Calls setupSearchListener to initialize search logic

        // Handle the floating action button to add a new article
        binding.floatingAddArticleButton.setOnClickListener { // Sets OnClickListener for FAB
            if (isNetworkAvailable()) { // Checks if network is available
                startActivity(Intent(this, AddArticle::class.java)) // Starts AddArticle activity
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.ERROR) // Shows error toast
            }
        }

        // Handle the back button press to show an exit confirmation dialog
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { // Adds OnBackPressedCallback
            override fun handleOnBackPressed() { // Overrides handleOnBackPressed
                showExitConfirmationDialog() // Shows exit confirmation dialog
            }
        })
    }

    // Called when the activity is currently visible
    override fun onResume() { // Overrides onResume method
        super.onResume() // Calls superclass onResume
        // Register network change receiver and attach database listeners
        registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)) // Registers network receiver
        
        // Offline handling: Check connection on resume
        if (!isNetworkAvailable()) { // Checks if network is unavailable
            wasOffline = true // Mark as offline so we know to show "Back Online" later // Sets wasOffline flag
            showToast("Please check your internet connection..", FancyToast.WARNING) // Shows warning toast
        } else { // Executed if network is available
            wasOffline = false // Resets wasOffline flag
        }
        
        loadUserProfile() // Loads user profile
        attachListeners() // Attaches database listeners
    }

    // Called when the activity is no longer interacting with the user
    override fun onPause() { // Overrides onPause method
        super.onPause() // Calls superclass onPause
        // Unregister network receiver and detach database listeners to avoid leaks
        try { // Starts try block for unregistering receiver
            unregisterReceiver(networkChangeReceiver) // Unregisters network receiver
        } catch (e: IllegalArgumentException) { // Catches IllegalArgumentException if not registered
            // Receiver not registered
        }
        detachListeners() // Detaches database listeners
    }

    // Sets up the RecyclerView with the BlogAdapter and LayoutManager
    private fun setupRecyclerView() { // Defines setupRecyclerView method
        blogAdapter = BlogAdapter( // Initializes BlogAdapter
            onReadMoreClick = { blogItem -> // Callback for read more click
                // Allow reading cached blogs even if offline
                val intent = Intent(this, ReadMore::class.java) // Creates intent for ReadMore activity
                intent.putExtra("blogItem", blogItem) // Puts blog item extra
                startActivity(intent) // Starts ReadMore activity
            },
            onLikeClick = { blogItem -> // Callback for like click
                if (!isNetworkAvailable()) { // Checks if network is unavailable
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                } else if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in and online
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles like
                }
            },
            onSaveClick = { blogItem -> // Callback for save click
                if (!isNetworkAvailable()) { // Checks if network is unavailable
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                } else if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in and online
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles save
                }
            }
        )
        binding.blogRecyclerView.apply { // Applies configuration to RecyclerView
            adapter = blogAdapter // Sets adapter
            layoutManager = LinearLayoutManager(this@HomePage) // Sets LayoutManager
        }
    }
    
    // Observes the global interaction state for UI updates
    private fun observeInteractionState() { // Defines observeInteractionState method
        lifecycleScope.launch { // Launches coroutine
            BlogRepository.interactionState.collect { state -> // Collects interaction state
                blogAdapter.updateInteractionState(state) // Updates adapter
            }
        }
    }

    // Initializes Firebase listeners for blogs
    private fun setupFirebaseListeners() { // Defines setupFirebaseListeners method
        blogsRef = database.getReference("blogs") // References blogs node
        // Keep the blogs synced for offline use
        blogsRef.keepSynced(true) // Enables offline sync for blogs
        
        // Listener for fetching all blog posts
        blogListener = object : ValueEventListener { // Creates ValueEventListener
            @SuppressLint("NotifyDataSetChanged") // Suppresses lint warning
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                allBlogs.clear() // Clears the list to avoid duplication
                val newItems = mutableListOf<BlogItemModel>() // Creates mutable list for items
                for (blogSnapshot in snapshot.children) { // Iterates through snapshot children
                    blogSnapshot.getValue(BlogItemModel::class.java)?.let { newItems.add(it) } // Adds deserialized item to list
                }
                newItems.reverse() // Reverses list (newest first)
                
                allBlogs.addAll(newItems) // Adds all fetched items to the allBlogs list
                
                // Initialize repository state with fetched blogs to ensure we have like counts
                BlogRepository.initializeState(newItems) // Initializes repository state
                
                blogAdapter.submitList(allBlogs) // Submits the allBlogs list to adapter
                binding.progressBar.visibility = View.GONE // Hides progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called on cancellation
                binding.progressBar.visibility = View.GONE // Hides progress bar
                showToast("Failed to load blogs: ${error.message}", FancyToast.ERROR) // Shows error toast
            }
        }
    }
    
    // Attaches the Firebase listeners to the database references
    private fun attachListeners() { // Defines attachListeners method
        blogListener?.let { blogsRef.addValueEventListener(it) } // Adds listener if not null
    }

    // Detaches the Firebase listeners
    private fun detachListeners() { // Defines detachListeners method
        blogListener?.let { blogsRef.removeEventListener(it) } // Removes listener if not null
    }

    // Loads the user's profile image from Firebase
    private fun loadUserProfile() { // Defines loadUserProfile method
        auth.currentUser?.let { user -> // Checks if current user exists
            val userRef = database.getReference("users").child(user.uid) // References user node
            // Use addValueEventListener instead of single for realtime updates and caching
            userRef.child("profileImage").addValueEventListener(object : ValueEventListener { // Adds ValueEventListener for profile image
                override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                    val dbProfileUrl = snapshot.getValue(String::class.java) // Gets profile URL from DB
                    if (!dbProfileUrl.isNullOrEmpty()) { // Checks if URL is valid
                        // Check if activity is still valid before loading image
                        if (!isFinishing && !isDestroyed) { // Checks activity state
                            Glide.with(this@HomePage).load(dbProfileUrl).into(binding.profileImage) // Loads image with Glide
                        }
                    } else { // Executed if URL is invalid
                        user.photoUrl?.let { authProfileUrl -> // Checks auth photo URL
                            if (!isFinishing && !isDestroyed) { // Checks activity state
                                Glide.with(this@HomePage).load(authProfileUrl).into(binding.profileImage) // Loads auth image
                            }
                        } ?: run { // Executed if auth photo is null
                            if (!isFinishing && !isDestroyed) { // Checks activity state
                                Glide.with(this@HomePage).load(R.drawable.default_avatar).into(binding.profileImage) // Loads default avatar
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) { // Called on cancellation
                     // Fallback to auth profile or default
                     user.photoUrl?.let { authProfileUrl -> // Checks auth photo URL
                        if (!isFinishing && !isDestroyed) { // Checks activity state
                            Glide.with(this@HomePage).load(authProfileUrl).into(binding.profileImage) // Loads auth image
                        }
                    } ?: run { // Executed if auth photo is null
                        if (!isFinishing && !isDestroyed) { // Checks activity state
                            Glide.with(this@HomePage).load(R.drawable.default_avatar).into(binding.profileImage) // Loads default avatar
                        }
                    }
                }
            })
        }
    }

    // BroadcastReceiver to monitor network connectivity changes
    private val networkChangeReceiver = object : BroadcastReceiver() { // Creates BroadcastReceiver for network changes
        override fun onReceive(context: Context, intent: Intent) { // Called when broadcast is received
            val isConnected = isNetworkAvailable() // Checks network availability
            
            if (isConnected && wasOffline) { // Checks if reconnected
                // Only show "Updating Feed" if we were previously offline and now we are back online
                showToast("Feed Updated..", FancyToast.INFO) // Shows info toast
                wasOffline = false // Resets offline flag
            } else if (!isConnected) { // Checks if disconnected
                // If we lose connection, mark it so we can show the toast when it comes back
                wasOffline = true // Sets offline flag
            }
        }
    }

    // Shows a confirmation dialog before exiting the app
    private fun showExitConfirmationDialog() { // Defines showExitConfirmationDialog method
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates SweetAlertDialog
            .setTitleText("Exit App") // Sets title
            .setContentText("You Really Want To Exit The App?") // Sets content text
            .setConfirmText("Yes") // Sets confirm button text
            .setConfirmClickListener { sDialog -> // Sets confirm listener
                sDialog.dismissWithAnimation() // Dismisses dialog
                finish() // Finishes activity
            }
            .setCancelText("Cancel") // Sets cancel button text
            .setCancelClickListener { it.dismissWithAnimation() } // Sets cancel listener
            .show() // Shows dialog
    }

    // Checks if the device is connected to the internet
    private fun isNetworkAvailable(): Boolean { // Defines isNetworkAvailable method
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false // Gets active network
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets network capabilities
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks Cellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks Ethernet
    }

    // Helper function to show a FancyToast message
    private fun showToast(message: String, @Suppress("SameParameterValue") type: Int) { // Defines showToast method
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Shows FancyToast
    }

    // Setup the search bar listener
    private fun setupSearchListener() { // Defines setupSearchListener method
        binding.searchBlock.setOnQueryTextListener(object : SearchView.OnQueryTextListener { // Sets OnQueryTextListener on SearchView
            override fun onQueryTextSubmit(query: String?): Boolean { // Called when query is submitted
                filterBlogs(query) // Calls filterBlogs with query
                return true // Returns true
            }

            override fun onQueryTextChange(newText: String?): Boolean { // Called when query text changes
                filterBlogs(newText) // Calls filterBlogs with new text
                return true // Returns true
            }
        })
    }

    // Filter blogs based on search query
    private fun filterBlogs(query: String?) { // Defines filterBlogs method
        val searchText = query?.lowercase().orEmpty() // Gets search text in lowercase
        if (searchText.isEmpty()) { // Checks if search text is empty
            blogAdapter.submitList(allBlogs) // Submits all blogs if search is empty
        } else { // Executed if search text is not empty
            val filteredList = allBlogs.filter { blog -> // Filters allBlogs
                blog.heading?.lowercase()?.contains(searchText) == true || // Checks heading match
                blog.blogPost?.lowercase()?.contains(searchText) == true // Checks body match
            }
            
            if (filteredList.isEmpty()) { // Checks if result is empty
                showToast("This blog doesn't exist", FancyToast.INFO) // Shows toast message
            }
            
            blogAdapter.submitList(filteredList) // Submits filtered list
        }
    }
}
