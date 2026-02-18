package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ayushcodes.blogapp.R
import com.ayushcodes.blogapp.adapter.BlogAdapter
import com.ayushcodes.blogapp.databinding.ActivityHomePageBinding
import com.ayushcodes.blogapp.model.BlogItemModel
import com.ayushcodes.blogapp.repository.BlogRepository
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.shashank.sony.fancytoastlib.FancyToast
import kotlinx.coroutines.launch

// Main activity of the app, displaying the feed of blog posts.
@Suppress("DEPRECATION") // Suppresses deprecation warnings for this class
class HomePage : AppCompatActivity() { // Defines HomePage class inheriting from AppCompatActivity

    companion object { // Companion object to hold state that survives activity recreation.
        private var isFirstLoad = true // Flag to check if it's the very first data load for the app session.
    }

    // Lazily initialize view binding for the activity layout
    private val binding: ActivityHomePageBinding by lazy { // Declares binding with lazy initialization
        ActivityHomePageBinding.inflate(layoutInflater) // Inflates the layout
    } // Closes the lazy initialization block.

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
    private var isOffline = false // Initializes the offline status flag to false.

    // List to store all loaded blogs for search functionality
    private val allBlogs = mutableListOf<BlogItemModel>() // Creates a list to hold all blog items
    private val allBlogsLock = Any() // Creates a lock object for thread-safe operations on allBlogs.

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        try { // Starts try block for persistence
            // Enable disk persistence for offline capabilities
            FirebaseDatabase.getInstance().setPersistenceEnabled(true) // Enables Firebase persistence
        } catch (e: Exception) { // Catches generic exception
            // Already enabled
        } // Closes the try-catch block.
        setContentView(binding.root) // Sets content view using binding
        if (isFirstLoad) { // Only show progress bar on the very first load.
            binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
        }

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Navigate to the user profile page when the profile card is clicked
        binding.cardView3.setOnClickListener { // Sets an OnClickListener on the profile card view
            if (isNetworkAvailable()) { // Checks if there is an active internet connection
                startActivity(Intent(this, ProfilePage::class.java)) // If online, starts the ProfilePage activity
            } else { // If offline
                showToast("Please check your internet connection..", FancyToast.INFO) // Show an informational toast to the user
            } // Closes the if-else block.
        } // Closes the setOnClickListener block.

        // Navigate to the saved articles page
        binding.saveArticleButton.setOnClickListener { // Sets OnClickListener for saved articles button
            startActivity(Intent(this, SavedArticlesActivity::class.java)) // Starts SavedArticlesActivity, which should work offline
        } // Closes the setOnClickListener block.

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
            } // Closes the if-else block.
        } // Closes the setOnClickListener block.

        // Handle the back button press to show an exit confirmation dialog
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { // Adds OnBackPressedCallback
            override fun handleOnBackPressed() { // Overrides handleOnBackPressed
                showExitConfirmationDialog() // Shows exit confirmation dialog
            } // Closes the handleOnBackPressed method.
        }) // Closes the addCallback block.
    } // Closes the onCreate method.

    // Called when the activity is currently visible
    override fun onResume() { // Overrides the onResume method.
        super.onResume() // Calls the superclass's onResume method.
        registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)) // Registers the network change receiver.

        // Set initial network state when activity becomes active
        if (!isNetworkAvailable()) { // Checks if the network is not available.
            if (!isOffline) { // Only show toast if state changes from online to offline
                showToast("You are offline. Showing cached data.", FancyToast.INFO) // Shows an offline toast.
                isOffline = true // Sets the offline flag to true.
            } // Closes the inner if block.
        } else { // If the network is available.
            if (isOffline) { // Checks if the device was previously offline.
                showToast("Feed Updated", FancyToast.SUCCESS) // Shows a success toast.
            }
            isOffline = false // Sets the offline flag to false.
        } // Closes the if-else block.

        loadUserProfile() // Calls the method to load the user profile.
        attachListeners() // Calls the method to attach Firebase listeners.
    } // Closes the onResume method.

    // Called when the activity is no longer interacting with the user
    override fun onPause() { // Overrides onPause method
        super.onPause() // Calls superclass onPause
        // Unregister network receiver and detach database listeners to avoid leaks
        try { // Starts try block for unregistering receiver
            unregisterReceiver(networkChangeReceiver) // Unregisters network receiver
        } catch (e: IllegalArgumentException) { // Catches IllegalArgumentException if not registered
            // Receiver not registered
        } // Closes the try-catch block.
        detachListeners() // Detaches database listeners
    } // Closes the onPause method.

    // Sets up the RecyclerView with the BlogAdapter and LayoutManager
    private fun setupRecyclerView() { // Defines setupRecyclerView method
        blogAdapter = BlogAdapter( // Initializes BlogAdapter
            onReadMoreClick = { blogItem -> // Callback for read more click
                // Allow reading cached blogs even if offline
                val intent = Intent(this, ReadMore::class.java) // Creates intent for ReadMore activity
                intent.putExtra("blogItem", blogItem) // Puts blog item extra
                startActivity(intent) // Starts ReadMore activity
            }, // Closes the onReadMoreClick lambda.
            onLikeClick = { blogItem -> // Callback for like click
                if (!isNetworkAvailable()) { // Checks if network is unavailable
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                } else if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in and online
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles like
                } // Closes the if-else block.
            }, // Closes the onLikeClick lambda.
            onSaveClick = { blogItem -> // Callback for save click
                if (!isNetworkAvailable()) { // Checks if network is unavailable
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                } else if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in and online
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles save
                } // Closes the if-else block.
            } // Closes the onSaveClick lambda.
        ) // Closes the BlogAdapter constructor call.
        binding.blogRecyclerView.apply { // Applies configuration to RecyclerView
            adapter = blogAdapter // Sets adapter
            layoutManager = LinearLayoutManager(this@HomePage) // Sets LayoutManager
        } // Closes the apply block.
    } // Closes the setupRecyclerView method.

    // Observes the global interaction state for UI updates
    private fun observeInteractionState() { // Defines observeInteractionState method
        lifecycleScope.launch { // Launches coroutine
            BlogRepository.interactionState.collect { state -> // Collects interaction state
                blogAdapter.updateInteractionState(state) // Updates adapter
            } // Closes the collect block.
        } // Closes the launch block.
    } // Closes the observeInteractionState method.

    // Initializes Firebase listeners for blogs
    private fun setupFirebaseListeners() { // Defines the setupFirebaseListeners method.
        // Get a reference to the "blogs" node in Firebase and limit to the last 50 items for performance
        blogsQuery = database.getReference("blogs").limitToLast(50) // This reduces the initial data load, making the feed appear faster.
        blogsQuery.keepSynced(true) // Keeps the data from this query synced for offline access

        blogListener = object : ValueEventListener { // Creates a new ValueEventListener.
            override fun onDataChange(snapshot: DataSnapshot) { // Overrides the onDataChange method.
                binding.progressBar.visibility = View.GONE // Hides the progress bar.
                val newItems = snapshot.children.mapNotNull { it.getValue(BlogItemModel::class.java) }.reversed() // Maps and reverses the new blog items.

                synchronized(allBlogsLock) { // Starts a synchronized block.
                    allBlogs.clear() // Clears the list of all blogs.
                    allBlogs.addAll(newItems) // Adds the new items to the list of all blogs.
                } // Closes the synchronized block.

                BlogRepository.initializeState(newItems) // Initializes the repository state with the new items.

                // Re-apply the current search filter to the new data
                val currentQuery = binding.searchBlock.query?.toString() // Gets the current search query.
                filterBlogs(currentQuery, showToastIfEmpty = false) // Filters the blogs based on the query.

                // If this is the initial load, show a "Feed Updated" toast.
                if (isFirstLoad) { // Checks if it's the initial load for the app session.
                    if (isNetworkAvailable()) { // Checks if the device has an active network connection.
                        showToast("Feed Updated", FancyToast.SUCCESS) // Shows a success toast.
                    }
                    isFirstLoad = false // After the first load, set this to false for the lifetime of the app process.
                } // Closes the if block.
            } // Closes the onDataChange method.

            override fun onCancelled(error: DatabaseError) { // Overrides the onCancelled method.
                binding.progressBar.visibility = View.GONE // Hides the progress bar.
                isFirstLoad = false // Also reset on error to avoid inconsistent state.
                showToast("Failed to load blogs: ${error.message}", FancyToast.ERROR) // Shows an error toast.
            } // Closes the onCancelled method.
        } // Closes the ValueEventListener object.
    } // Closes the setupFirebaseListeners method.

    // Attaches the Firebase listeners to the database query
    private fun attachListeners() { // Defines attachListeners method
        blogListener?.let { blogsQuery.addValueEventListener(it) } // Adds listener if not null
    } // Closes the attachListeners method.

    // Detaches the Firebase listeners
    private fun detachListeners() { // Defines detachListeners method
        blogListener?.let { blogsQuery.removeEventListener(it) } // Removes listener if not null
    } // Closes the detachListeners method.

    // Loads the user's profile image from Firebase
    private fun loadUserProfile() { // Defines the loadUserProfile method.
        auth.currentUser?.let { user -> // Executes if the current user is not null.
            val userRef = database.getReference("users").child(user.uid) // Gets a reference to the user's data in the database.
            userRef.child("profileImage").addValueEventListener(object : ValueEventListener { // Adds a listener for the user's profile image.
                override fun onDataChange(snapshot: DataSnapshot) { // Overrides the onDataChange method.
                    if (isFinishing || isDestroyed) return // Returns if the activity is finishing or destroyed.

                    val dbProfileUrl = snapshot.getValue(String::class.java) // Gets the profile image URL from the database.
                    val imageUrl = dbProfileUrl ?: user.photoUrl?.toString() // Uses the database URL or the user's photo URL.

                    Glide.with(this@HomePage) // Initializes Glide for image loading
                        .load(imageUrl ?: R.drawable.default_avatar) // Sets the image URL to load
                        .into(binding.profileImage) // Loads the image into the specified ImageView
                } // Closes the onDataChange method.

                override fun onCancelled(error: DatabaseError) { // Overrides the onCancelled method.
                    if (isFinishing || isDestroyed) return // Returns if the activity is finishing or destroyed.
                    // Fallback to auth profile or default
                    val imageUrl = user.photoUrl?.toString() ?: R.drawable.default_avatar // Uses the user's photo URL or a default avatar.
                    Glide.with(this@HomePage) // Initializes Glide for image loading
                        .load(imageUrl) // Sets the image URL to load
                        .into(binding.profileImage) // Loads the image into the specified ImageView
                } // Closes the onCancelled method.
            }) // Closes the addValueEventListener block.
        } // Closes the let block.
    } // Closes the loadUserProfile method.

    // BroadcastReceiver to monitor network connectivity changes
    private val networkChangeReceiver = object : BroadcastReceiver() { // Creates a new BroadcastReceiver.
        override fun onReceive(context: Context, intent: Intent) { // Overrides the onReceive method.
            val isConnected = isNetworkAvailable() // Checks if the device is connected to the network.

            // Transition from OFFLINE to ONLINE
            if (isConnected && isOffline) { // Checks if the device is connected and was previously offline.
                isOffline = false // Sets the offline flag to false.
                binding.progressBar.visibility = View.VISIBLE // Make progress bar visible
                showToast("Updating feed....", FancyToast.INFO) // Show updating feed toast
                // Force a data refresh by re-attaching the listener. This ensures onDataChange is called.
                detachListeners() // Detaches the listeners.
                attachListeners() // Attaches the listeners.
            } // Closes the if block.
            // Transition from ONLINE to OFFLINE
            else if (!isConnected && !isOffline) { // Checks if the device is not connected and was previously online.
                showToast("You are offline. Showing cached data.", FancyToast.INFO) // Shows an offline toast.
                isOffline = true // Sets the offline flag to true.
            } // Closes the else-if block.
        } // Closes the onReceive method.
    } // Closes the BroadcastReceiver object.

    // Shows a confirmation dialog before exiting the app
    private fun showExitConfirmationDialog() { // Defines showExitConfirmationDialog method
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates SweetAlertDialog
            .setTitleText("Exit App") // Sets title
            .setContentText("You Really Want To Exit The App?") // Sets content text
            .setConfirmText("Yes") // Sets confirm button text
            .setConfirmClickListener { sDialog -> // Sets confirm listener
                sDialog.dismissWithAnimation() // Dismisses dialog
                finish() // Finishes activity
            } // Closes the setConfirmClickListener block.
            .setCancelText("Cancel") // Sets cancel button text
            .setCancelClickListener { it.dismissWithAnimation() } // Sets cancel listener
            .show() // Shows dialog
    } // Closes the showExitConfirmationDialog method.

    // Checks if the device is connected to the internet
    private fun isNetworkAvailable(): Boolean { // Defines isNetworkAvailable method
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager service.
        val network = connectivityManager.activeNetwork ?: return false // Gets the active network or returns false.
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets the network capabilities or returns false.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks for Wi-Fi transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks for cellular transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks for Ethernet transport.
    } // Closes the isNetworkAvailable method.

    // Helper function to show a FancyToast message
    private fun showToast(message: String, @Suppress("SameParameterValue") type: Int) { // Defines showToast method
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Shows a FancyToast.
    } // Closes the showToast method.

    // Setup the search bar listener
    private fun setupSearchListener() { // Defines the setupSearchListener method.
        binding.searchBlock.setOnQueryTextListener(object : SearchView.OnQueryTextListener { // Sets a query text listener on the search block.
            override fun onQueryTextSubmit(query: String?): Boolean { // Overrides the onQueryTextSubmit method.
                filterBlogs(query) // Filters the blogs based on the query.
                return true // Returns true to indicate the query has been handled.
            } // Closes the onQueryTextSubmit method.

            override fun onQueryTextChange(newText: String?): Boolean { // Overrides the onQueryTextChange method.
                filterBlogs(newText) // Filters the blogs based on the new text.
                return true // Returns true to indicate the change has been handled.
            } // Closes the onQueryTextChange method.
        }) // Closes the setOnQueryTextListener block.
    } // Clses the setupSearchListener method.

    // Filter blogs based on search query
    private fun filterBlogs(query: String?, showToastIfEmpty: Boolean = true) { // Defines the filterBlogs method.
        val searchText = query?.lowercase().orEmpty() // Converts the query to lowercase or an empty string.

        val listToSubmit = synchronized(allBlogsLock) { // Starts a synchronized block.
            if (searchText.isEmpty()) { // Checks if the search text is empty.
                allBlogs.toList() // Return a copy of the full list
            } else { // If the search text is not empty.
                allBlogs.filter { blog -> // Filters the list of all blogs.
                    blog.heading?.lowercase()?.contains(searchText) == true || // Checks if the heading contains the search text.
                    blog.blogPost?.lowercase()?.contains(searchText) == true // Checks if the blog post contains the search text.
                } // Closes the filter block.
            } // Closes the if-else block.
        } // Closes the synchronized block.

        if (showToastIfEmpty && searchText.isNotEmpty() && listToSubmit.isEmpty()) { // Checks if a toast should be shown.
            showToast("This blog doesn't exist", FancyToast.INFO) // Shows an info toast.
        } // Closes the if block.

        blogAdapter.submitList(listToSubmit) // Submits the filtered list to the adapter.
    } // Closes the filterBlogs method.
} // Closes the HomePage class.
