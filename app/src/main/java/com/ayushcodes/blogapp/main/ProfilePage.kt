@file:Suppress("DEPRECATION")

package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Bundle // Imports Bundle to pass data between components
import android.view.View // Imports View class for UI elements
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as base class
import androidx.core.view.ViewCompat // Imports ViewCompat for compatibility
import androidx.core.view.WindowInsetsCompat // Imports WindowInsetsCompat for window insets
import cn.pedant.SweetAlert.SweetAlertDialog // Imports SweetAlertDialog for alerts
import com.ayushcodes.blogapp.R // Imports R class for resources
import com.ayushcodes.blogapp.databinding.ActivityProfilePageBinding // Imports generated binding class
import com.ayushcodes.blogapp.main.adapters.ViewPagerAdapter // Imports ViewPagerAdapter for tabs
import com.ayushcodes.blogapp.register.WelcomeScreen // Imports WelcomeScreen activity
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.tabs.TabLayoutMediator // Imports TabLayoutMediator for tabs
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for custom toasts
import java.text.SimpleDateFormat // Imports SimpleDateFormat for date formatting
import java.util.Date // Imports Date class
import java.util.Locale // Imports Locale class

// Activity to display the user's profile information.
@Suppress("DEPRECATION") // Suppresses deprecation warnings
class ProfilePage : AppCompatActivity() { // Defines ProfilePage class inheriting from AppCompatActivity

    private lateinit var binding: ActivityProfilePageBinding // Declares binding variable
    private val sharedViewModel: SharedViewModel by viewModels() // Get a reference to the SharedViewModel.

    // Firebase Authentication instance to manage user login
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance

    // Firebase Database instance for user data operations
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Google Sign-In client.
    private lateinit var googleSignInClient: GoogleSignInClient // Declares GoogleSignInClient instance

    private var profileImageUrl: String? = null // EDITED: Stores the URL of the profile image.

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        enableEdgeToEdge() // Enables edge-to-edge display
        binding = ActivityProfilePageBinding.inflate(layoutInflater) // Inflates layout
        setContentView(binding.root) // Sets content view

        // If the user is offline, show a toast and finish the activity before setting the content view.
        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty page.
            return // Stops further execution of the onCreate method.
        }

        // Adjust padding to accommodate system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets -> // Sets window insets listener
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()) // Gets system bars insets
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom) // Sets padding
            insets // Returns insets
        }

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Configure Google Sign-In options.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN) // Builds GoogleSignInOptions
            .requestIdToken(getString(R.string.default_web_client_id)) // Requests ID token using web client ID
            .requestEmail() // Requests user email
            .build() // Builds the options
        googleSignInClient = GoogleSignIn.getClient(this, gso) // Gets the GoogleSignInClient

        // Set up tabs for viewing user's blogs and liked blogs
        setupTabs() // Calls setupTabs method
        // Set up click listeners for UI elements
        setupClickListeners() // Calls setupClickListeners method
        setupProfileImageClickListener() // EDITED: Sets up the click listener for the profile image.
    }

    override fun onResume() { // Add onResume lifecycle method to refresh data when the screen becomes visible.
        super.onResume() // Call the superclass's onResume method.
        loadUserData() // Load user data every time the activity resumes to ensure it's up-to-date.
        sharedViewModel.notifyProfileUpdated() // Notify the fragments that the profile data has been updated.
    }

    private fun setupProfileImageClickListener() { // EDITED: Defines a new function to set up the click listener for the profile image.
        binding.profileImage.setOnClickListener { // EDITED: Sets a click listener on the user's profile image.
            profileImageUrl?.let { url -> // EDITED: Checks if the profile image URL is not null.
                val intent = Intent(this, FullScreenImageActivity::class.java) // EDITED: Creates an intent to start the FullScreenImageActivity.
                intent.putExtra("image_url", url) // EDITED: Passes the image URL to the new activity.
                startActivity(intent) // EDITED: Starts the activity.
            }
        }
    }

    // Configures the ViewPager and TabLayout
    private fun setupTabs() { // Defines setupTabs method
        val adapter = ViewPagerAdapter(supportFragmentManager, lifecycle) // Creates ViewPagerAdapter
        binding.viewPager.adapter = adapter // Sets adapter to ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position -> // Attaches TabLayoutMediator
            when (position) { // Switches on position
                0 -> tab.text = "My Blogs" // Sets text for first tab
                1 -> tab.text = "Liked Blogs" // Sets text for second tab
            }
        }.attach() // Attaches mediator
    }

    // Sets up click listeners for buttons and interactive elements
    private fun setupClickListeners() { // Defines setupClickListeners method

        // Handle back button click
        binding.backButton.setOnClickListener { // Sets listener for back button
            finish() // Finishes activity
        }

        // Handle sign out button click
        binding.signOutButton.setOnClickListener { // Sets listener for sign out button
            showSignOutConfirmation() // Calls showSignOutConfirmation method
        }

        binding.editProfileButton.setOnClickListener {
            val intent = Intent(this, EditProfile::class.java) // Creates an Intent to navigate to the EditProfile screen.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // This flag brings an existing instance of the activity to the front of the stack, rather than creating a new one.
            startActivity(intent) // Starts the EditProfile activity with the specified flag to ensure proper navigation.
        }
    }

    // Shows a confirmation dialog before signing out
    private fun showSignOutConfirmation() { // Defines showSignOutConfirmation method
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates SweetAlertDialog with warning type
            .setTitleText("Sign Out") // Sets title text
            .setContentText("Are you sure you want to sign out?") // Sets content text
            .setConfirmText("Yes") // Sets confirm button text
            .setConfirmClickListener { sDialog -> // Sets confirm button listener
                showToast("Signing Out...", FancyToast.INFO) // Shows toast message for signing out
                auth.signOut() // Signs out the user from Firebase
                googleSignInClient.signOut().addOnCompleteListener { // sign out from google
                    val intent = Intent(this, WelcomeScreen::class.java) // Creates intent for WelcomeScreen
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clears task stack
                    startActivity(intent) // Starts WelcomeScreen activity
                    showToast("Signed Out...", FancyToast.SUCCESS) // Shows toast message for signed out
                    sDialog.dismissWithAnimation() // Dismisses the dialog
                    finish() // Finishes the current activity
                }
            }
            .setCancelText("No") // Sets cancel button text
            .setCancelClickListener { it.dismissWithAnimation() } // Sets cancel button listener
            .show() // Shows the dialog
    }

    // Loads user data from Firebase Database to populate the UI
    private fun loadUserData() { // Defines loadUserData method
        binding.progressBar.visibility = View.VISIBLE // Shows progress bar
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        val userReference = database.reference.child("users").child(userId) // References user node

        userReference.addListenerForSingleValueEvent(object : ValueEventListener { // Use addListenerForSingleValueEvent to avoid multiple calls
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                if (isDestroyed || isFinishing) return // Checks activity state

                val name = snapshot.child("name").getValue(String::class.java) // Gets name
                val emailFromDb = snapshot.child("email").getValue(String::class.java) // Gets email
                profileImageUrl = snapshot.child("profileImage").getValue(String::class.java) // EDITED: Stores the profile image URL.
                val creationDate = snapshot.child("creationDate").getValue(String::class.java) // Gets creation date
                val authEmail = auth.currentUser?.email // Gets auth email

                binding.profileFullName.setText(name ?: auth.currentUser?.displayName) // Sets name text
                binding.profileEmail.setText(authEmail) // Sets email text
                binding.memberSinceDate.text = creationDate // Sets member since text

                // If creation date is missing, set it using metadata
                if (creationDate == null) { // Checks if creation date is null
                    val creationTimestamp = auth.currentUser?.metadata?.creationTimestamp ?: System.currentTimeMillis() // Gets creation timestamp
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp)) // Formats date
                    userReference.child("creationDate").setValue(formattedDate) // Sets creation date in DB
                    binding.memberSinceDate.text = formattedDate // Sets member since text
                }

                // Ensure email in database matches authenticated user's email
                if (authEmail != null && authEmail != emailFromDb) { // Checks if email mismatches
                    userReference.child("email").setValue(authEmail) // Updates email in DB
                }

                // Load profile image using Glide
                if (!profileImageUrl.isNullOrEmpty()) { // Checks if profile image exists
                    Glide.with(this@ProfilePage)
                        .load(profileImageUrl) // Sets the image URL to load
                        .placeholder(R.drawable.default_avatar) // Displays a default image while loading
                        .error(R.drawable.default_avatar) // Displays a default image if loading fails
                        .into(binding.profileImage) // Loads the image into the specified ImageView
                } else { // Executed if no profile image
                     auth.currentUser?.photoUrl?.let { // Checks auth photo URL
                        Glide.with(this@ProfilePage)
                            .load(it) // Sets the image URL to load
                            .placeholder(R.drawable.default_avatar) // Displays a default image while loading
                            .error(R.drawable.default_avatar) // Displays a default image if loading fails
                            .into(binding.profileImage) // Loads the image into the specified ImageView
                     } ?: Glide.with(this@ProfilePage)
                         .load(R.drawable.default_avatar) // Sets the default image to load
                         .into(binding.profileImage) // Loads the image into the specified ImageView
                }
                binding.progressBar.visibility = View.GONE // Hides progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called on cancellation
                if (!isDestroyed || isFinishing) { // Checks activity state
                    binding.progressBar.visibility = View.GONE // Hides progress bar
                    showToast("Something went wrong.", FancyToast.ERROR) // Shows an error toast
                }
            }
        })
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
        if (!isDestroyed && !isFinishing) { // Checks activity state
            FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Shows FancyToast
        }
    }
}
