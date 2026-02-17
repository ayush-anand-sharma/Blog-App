package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.annotation.SuppressLint // Imports SuppressLint for suppressing warnings
import android.content.Context // Imports Context to access application environment
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Build // Imports Build class for version checking
import android.os.Bundle // Imports Bundle to pass data between components
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as base class
import androidx.core.view.ViewCompat // Imports ViewCompat for compatibility
import androidx.core.view.WindowInsetsCompat // Imports WindowInsetsCompat for window insets
import androidx.lifecycle.Lifecycle // Imports Lifecycle class
import androidx.lifecycle.lifecycleScope // Imports lifecycleScope for managing coroutines
import androidx.lifecycle.repeatOnLifecycle // Imports repeatOnLifecycle for lifecycle-aware collection
import com.ayushcodes.blogapp.R // Imports R class for resources
import com.ayushcodes.blogapp.databinding.ActivityReadMoreBinding // Imports generated binding class
import com.ayushcodes.blogapp.model.BlogItemModel // Imports BlogItemModel data class
import com.ayushcodes.blogapp.repository.BlogRepository
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for authentication
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for custom toasts
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Activity to display the detailed view of a selected blog post.
@SuppressLint("Unused") // Suppresses unused warning
class ReadMore : AppCompatActivity() { // Defines ReadMore class inheriting from AppCompatActivity
    // Lazily initialize view binding for the activity layout
    private lateinit var binding: ActivityReadMoreBinding // Declares binding variable

    // Current authenticated user
    private val currentUser = FirebaseAuth.getInstance().currentUser // Gets current user

    // The blog item to display details for
    private var blogItem: BlogItemModel? = null // Declares blogItem variable, nullable

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate

        // If the user is offline, show a toast and finish the activity before setting the content view.
        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty or partially loaded page.
            return // Stops further execution of the onCreate method.
        }

        binding = ActivityReadMoreBinding.inflate(layoutInflater) // Inflates layout
        enableEdgeToEdge() // Enables edge-to-edge display
        setContentView(binding.root) // Sets content view

        // Adjust padding to accommodate system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets -> // Sets window insets listener
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()) // Gets system bars insets
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom) // Sets padding
            insets // Returns insets
        }

        // Set click listener for the back button
        binding.BackImageButton.setOnClickListener { // Sets listener for back button
            finish() // Finishes activity
        }

        // Retrieve the blog item from the intent extras
        blogItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Checks Android version
            intent.getParcelableExtra("blogItem", BlogItemModel::class.java) // Gets parcelable extra for newer versions
        } else { // Executed for older versions
            @Suppress("DEPRECATION") // Suppresses deprecation warning
            intent.getParcelableExtra<BlogItemModel>("blogItem") // Gets parcelable extra for older versions
        }

        // If the blog item is missing, display an error and close the activity
        if (blogItem == null) { // Checks if blog item is null
            showToast("Something went wrong", FancyToast.ERROR) // Shows error toast
            finish() // Finishes activity
            return // Returns
        }

        // Initialize state in ViewModel (in case it wasn't already)
        BlogRepository.initializeState(listOf(blogItem!!)) // Initializes ViewModel state

        // Bind the blog data to the UI elements
        binding.titleText.text = blogItem!!.heading // Sets title text
        binding.profileName.text = blogItem!!.fullName // Sets profile name text
        binding.profileDate.text = blogItem!!.date // Sets date text
        binding.descriptionText.text = blogItem!!.blogPost // Sets description text

        // Load the author's profile image
        val userImageUrl = blogItem!!.profileImage // Gets profile image URL
        Glide.with(this) // Initializes Glide for image loading
            .load(userImageUrl) // Sets the image URL to load
            .placeholder(R.drawable.default_avatar) // Displays a default image while loading
            .error(R.drawable.default_avatar) // Displays a default image if loading fails
            .into(binding.profileImage) // Loads the image into the specified ImageView

        val blogId = blogItem!!.blogId!! // Gets blog ID

        // Observe global interaction state
        lifecycleScope.launch { // Launches coroutine
            repeatOnLifecycle(Lifecycle.State.STARTED) { // Repeats on started state
                BlogRepository.interactionState.collect { stateMap -> // Collects interaction state map
                    val state = stateMap[blogId] // Gets state for this blog
                    if (state != null) {
                        val isLiked = state.isLiked
                        val isSaved = state.isSaved

                        binding.likeFloatingButton.setImageResource(if (isLiked) R.drawable.red_like_heart_icon else R.drawable.white_and_black_like_heart_icon) // Sets like icon
                        binding.saveFloatingButton.setImageResource(if (isSaved) R.drawable.bookmark_semi_red_icon else R.drawable.bookmark_red_icon) // Sets save icon
                    }
                }
            }
        }

        // Setup interaction listeners
        binding.likeFloatingButton.setOnClickListener { // Sets listener for like button
            if (currentUser == null) { // Checks if user is not logged in
                showToast("Please login first", FancyToast.INFO) // Shows login prompt
                return@setOnClickListener // Returns
            }
            if (!isNetworkAvailable()) { // Checks if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                return@setOnClickListener // Returns
            }
            BlogRepository.toggleLike(blogItem!!.blogId!!, blogItem!!) // Toggles like in ViewModel
        }

        binding.saveFloatingButton.setOnClickListener { // Sets listener for save button
            if (currentUser == null) { // Checks if user is not logged in
                showToast("Please login first", FancyToast.INFO) // Shows login prompt
                return@setOnClickListener // Returns
            }
            if (!isNetworkAvailable()) { // Checks if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
                return@setOnClickListener // Returns
            }
            BlogRepository.toggleSave(blogItem!!.blogId!!, blogItem!!) // Toggles save in ViewModel
        }
    }

    // Checks if the device has an active network connection
    private fun isNetworkAvailable(): Boolean { // Defines isNetworkAvailable method
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false // Gets active network
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets network capabilities
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks Cellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks Ethernet
    }

    // Shows a custom toast message
    private fun showToast(message: String, type: Int) { // Defines showToast method
        if (!isFinishing && !isDestroyed) { // Checks activity state
             FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon, false).show() // Shows FancyToast
        }
    }
}
