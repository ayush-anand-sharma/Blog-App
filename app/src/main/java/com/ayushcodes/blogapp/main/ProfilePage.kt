package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.os.Bundle // Imports Bundle to pass data between components
import android.view.View // Imports View class for UI elements
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as base class
import androidx.core.view.ViewCompat // Imports ViewCompat for compatibility
import androidx.core.view.WindowInsetsCompat // Imports WindowInsetsCompat for window insets
import cn.pedant.SweetAlert.SweetAlertDialog // Imports SweetAlertDialog for alerts
import com.ayushcodes.blogapp.R // Imports R class for resources
import com.ayushcodes.blogapp.databinding.ActivityProfilePageBinding // Imports generated binding class
import com.ayushcodes.blogapp.main.adapters.ViewPagerAdapter // Imports ViewPagerAdapter for tabs
import com.ayushcodes.blogapp.register.WelcomeScreen // Imports WelcomeScreen activity
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.bumptech.glide.request.RequestOptions // Imports RequestOptions for Glide
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

    // Lazily initialize view binding for the activity layout
    private lateinit var binding: ActivityProfilePageBinding // Declares binding variable

    // Firebase Authentication instance to manage user login
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance

    // Firebase Database instance for user data operations
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        enableEdgeToEdge() // Enables edge-to-edge display

        // If the user is offline, show a toast and finish the activity before setting the content view.
        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty page.
            return // Stops further execution of the onCreate method.
        }

        binding = ActivityProfilePageBinding.inflate(layoutInflater) // Inflates layout
        setContentView(binding.root) // Sets content view

        // Adjust padding to accommodate system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets -> // Sets window insets listener
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()) // Gets system bars insets
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom) // Sets padding
            insets // Returns insets
        }

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Set up tabs for viewing user's blogs and liked blogs
        setupTabs() // Calls setupTabs method
        // Load the user's data from Firebase
        loadUserData() // Calls loadUserData method
        // Set up click listeners for UI elements
        setupClickListeners() // Calls setupClickListeners method
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
            val intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
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
                val intent = Intent(this, WelcomeScreen::class.java) // Creates intent for WelcomeScreen
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clears task stack
                startActivity(intent) // Starts WelcomeScreen activity
                showToast("Signed Out...", FancyToast.SUCCESS) // Shows toast message for signed out
                sDialog.dismissWithAnimation() // Dismisses the dialog
                finish() // Finishes the current activity
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

        userReference.addValueEventListener(object : ValueEventListener { // Adds ValueEventListener
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                if (isDestroyed || isFinishing) return // Checks activity state

                val name = snapshot.child("name").getValue(String::class.java) // Gets name
                val emailFromDb = snapshot.child("email").getValue(String::class.java) // Gets email
                val profileImage = snapshot.child("profileImage").getValue(String::class.java) // Gets profile image
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
                if (!profileImage.isNullOrEmpty()) { // Checks if profile image exists
                    Glide.with(this@ProfilePage) // Loads with Glide
                        .load(profileImage) // Loads image
                        .apply(RequestOptions.circleCropTransform()) // Applies circle crop
                        .placeholder(R.drawable.default_avatar) // Sets placeholder
                        .error(R.drawable.default_avatar) // Sets error
                        .into(binding.profileImage) // Into profile image view
                } else { // Executed if no profile image
                     auth.currentUser?.photoUrl?.let { // Checks auth photo URL
                        Glide.with(this@ProfilePage) // Loads with Glide
                            .load(it) // Loads URL
                            .apply(RequestOptions.circleCropTransform()) // Applies circle crop
                            .placeholder(R.drawable.default_avatar) // Sets placeholder
                            .error(R.drawable.default_avatar) // Sets error
                            .into(binding.profileImage) // Into profile image view
                     } ?: Glide.with(this@ProfilePage) // Executed if no auth photo
                         .load(R.drawable.default_avatar) // Loads default avatar
                         .apply(RequestOptions.circleCropTransform()) // Applies circle crop
                         .into(binding.profileImage) // Into profile image view
                }
                binding.progressBar.visibility = View.GONE // Hides progress bar
            }

            override fun onCancelled(error: DatabaseError) { // Called on cancellation
                if (!isDestroyed || isFinishing) { // Checks activity state
                    binding.progressBar.visibility = View.GONE // Hides progress bar
                    showToast("Something went wrong.", FancyToast.ERROR) // Shows error toast
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
