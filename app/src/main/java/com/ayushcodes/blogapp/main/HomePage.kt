package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.content.BroadcastReceiver // Imports BroadcastReceiver for handling system broadcasts
import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.content.IntentFilter // Imports IntentFilter for filtering broadcasts
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Bundle // Imports Bundle to pass data between components
import androidx.appcompat.widget.SearchView // Imports SearchView for search functionality
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
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.Query // Imports Query for creating specific database queries
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

    // Firebase database query for the "blogs" node
    private lateinit var blogsQuery: Query // Declares Query for blogs
    
    // Listener for changes in the "blogs" node
    private var blogListener: ValueEventListener? = null // Declares ValueEventListener for blogs, nullable

    // Track if we are currently offline
    private var isOffline = false
    private var isInitialLoad = true // Flag to check if it's the first data load.

    // List to store all loaded blogs for search functionality
    private val allBlogs = mutableListOf<BlogItemModel>() // Creates a list to hold all blog items
    private val allBlogsLock = Any()

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
        binding.cardView3.setOnClickListener { // Sets an OnClickListener on the profile card view
            if (isNetworkAvailable()) { // Checks if there is an active internet connection
                startActivity(Intent(this, ProfilePage::class.java)) // If online, starts the ProfilePage activity
            } else { // If offline
                showToast("Please check your internet connection..", FancyToast.INFO) // Show an informational toast to the user
            }
        }

        // Navigate to the saved articles page
        binding.saveArticleButton.setOnClickListener { // Sets OnClickListener for saved articles button
            startActivity(Intent(this, SavedArticlesActivity::class.java)) // Starts SavedArticlesActivity, which should work offline
        }

        // Initialize the RecyclerView and Firebase listeners
        setupRecyclerView() // Calls setupRecyclerView
        setupFirebaseListeners() // Calls setupFirebaseListeners
        observeInteractionState() // Calls observeInteractionState
        setupSearchListener() // Calls setupSearchListener to initialize search logic

        // Handle the floating action button to add a new article
        binding.floatingAddArticleButton.setOnClickListener { // Sets an OnClickListener on the floating action button
            if (isNetworkAvailable()) { // Checks if there is an active internet connection
                startActivity(Intent(this, AddArticle::class.java)) // If online, starts the AddArticle activity
            } else { // If offline
                showToast("Please check your internet connection..", FancyToast.INFO) // Show an informational toast to the user
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
    override fun onResume() {
        super.onResume()
        registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        // Set initial network state when activity becomes active
        if (!isNetworkAvailable()) {
            if (!isOffline) { // Only show toast if state changes from online to offline
                showToast("You are offline. Showing cached data.", FancyToast.INFO)
                isOffline = true
            }
        } else {
            isOffline = false // We are online
        }

        loadUserProfile()
        attachListeners()
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
    private fun setupFirebaseListeners() {
        // Get a reference to the "blogs" node in Firebase and limit to the last 50 items for performance
        blogsQuery = database.getReference("blogs").limitToLast(50) // This reduces the initial data load, making the feed appear faster.
        blogsQuery.keepSynced(true) // Keeps the data from this query synced for offline access

        blogListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newItems = snapshot.children.mapNotNull { it.getValue(BlogItemModel::class.java) }.reversed()

                synchronized(allBlogsLock) {
                    allBlogs.clear()
                    allBlogs.addAll(newItems)
                }

                BlogRepository.initializeState(newItems)
                
                // Re-apply the current search filter to the new data
                val currentQuery = binding.searchBlock.query?.toString()
                filterBlogs(currentQuery, showToastIfEmpty = false)

                // If this isn't the initial load, show a "Feed Updated" toast.
                if (!isInitialLoad) {
                    showToast("Feed Updated", FancyToast.SUCCESS)
                }
                isInitialLoad = false // After the first load, set this to false.
            }

            override fun onCancelled(error: DatabaseError) {
                isInitialLoad = false // Also reset on error to avoid inconsistent state.
                showToast("Failed to load blogs: ${error.message}", FancyToast.ERROR)
            }
        }
    }
    
    // Attaches the Firebase listeners to the database query
    private fun attachListeners() { // Defines attachListeners method
        blogListener?.let { blogsQuery.addValueEventListener(it) } // Adds listener if not null
    }

    // Detaches the Firebase listeners
    private fun detachListeners() { // Defines detachListeners method
        blogListener?.let { blogsQuery.removeEventListener(it) } // Removes listener if not null
    }

    // Loads the user's profile image from Firebase
    private fun loadUserProfile() {
        auth.currentUser?.let { user ->
            val userRef = database.getReference("users").child(user.uid)
            userRef.child("profileImage").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    val dbProfileUrl = snapshot.getValue(String::class.java)
                    val imageUrl = dbProfileUrl ?: user.photoUrl?.toString()

                    Glide.with(this@HomePage)
                        .load(imageUrl ?: R.drawable.default_avatar)
                        .into(binding.profileImage)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isFinishing || isDestroyed) return
                    // Fallback to auth profile or default
                    val imageUrl = user.photoUrl?.toString() ?: R.drawable.default_avatar
                    Glide.with(this@HomePage)
                        .load(imageUrl)
                        .into(binding.profileImage)
                }
            })
        }
    }

    // BroadcastReceiver to monitor network connectivity changes
    private val networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isConnected = isNetworkAvailable()

            // Transition from OFFLINE to ONLINE
            if (isConnected && isOffline) {
                showToast("Back online. Updating feed...", FancyToast.SUCCESS)
                isOffline = false
                // Force a data refresh by re-attaching the listener. This ensures onDataChange is called.
                detachListeners()
                attachListeners()
            }
            // Transition from ONLINE to OFFLINE
            else if (!isConnected && !isOffline) {
                showToast("You are offline. Showing cached data.", FancyToast.INFO)
                isOffline = true
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
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // Helper function to show a FancyToast message
    private fun showToast(message: String, @Suppress("SameParameterValue") type: Int) { // Defines showToast method
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show()
    }

    // Setup the search bar listener
    private fun setupSearchListener() {
        binding.searchBlock.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterBlogs(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterBlogs(newText)
                return true
            }
        })
    }

    // Filter blogs based on search query
    private fun filterBlogs(query: String?, showToastIfEmpty: Boolean = true) {
        val searchText = query?.lowercase().orEmpty()

        val listToSubmit = synchronized(allBlogsLock) {
            if (searchText.isEmpty()) {
                allBlogs.toList() // Return a copy of the full list
            } else {
                allBlogs.filter { blog ->
                    blog.heading?.lowercase()?.contains(searchText) == true ||
                    blog.blogPost?.lowercase()?.contains(searchText) == true
                }
            }
        }

        if (showToastIfEmpty && searchText.isNotEmpty() && listToSubmit.isEmpty()) {
            showToast("This blog doesn't exist", FancyToast.INFO)
        }

        blogAdapter.submitList(listToSubmit)
    }
}
