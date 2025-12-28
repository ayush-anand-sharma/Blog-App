package com.ayushcodes.blogapp.main // Defines the package name for this file

// Import necessary Android and library classes
import android.annotation.SuppressLint // Imports SuppressLint for suppressing warnings
import android.content.Context // Imports Context to access application environment
import android.content.Intent // Imports Intent to launch activities
import android.graphics.Bitmap // Imports Bitmap for image handling
import android.net.ConnectivityManager // Imports ConnectivityManager to handle network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities to check network capabilities
import android.net.Uri // Imports Uri for handling URIs
import android.os.Bundle // Imports Bundle to pass data between components
import android.provider.MediaStore // Imports MediaStore for accessing media
import android.text.InputType // Imports InputType for input field types
import android.view.View // Imports View class for UI elements
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts for results
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
import com.google.firebase.auth.UserProfileChangeRequest // Imports UserProfileChangeRequest for profile updates
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for data changes
import com.google.firebase.storage.FirebaseStorage // Imports FirebaseStorage for file storage
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for custom toasts
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream for image compression
import java.text.SimpleDateFormat // Imports SimpleDateFormat for date formatting
import java.util.Date // Imports Date class
import java.util.Locale // Imports Locale class

// Activity to display and edit the user's profile information.
@Suppress("DEPRECATION") // Suppresses deprecation warnings
class ProfilePage : AppCompatActivity() { // Defines ProfilePage class inheriting from AppCompatActivity

    // Lazily initialize view binding for the activity layout
    private lateinit var binding: ActivityProfilePageBinding // Declares binding variable
    
    // Firebase Authentication instance to manage user login
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    
    // Firebase Database instance for user data operations
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance
    
    // Firebase Storage instance for profile image operations
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance

    // Activity result launcher to pick an image from the device gallery
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { // Registers activity result launcher for content
        uri: Uri? -> // Lambda callback with URI
        uri?.let { // Checks if URI is not null
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it) // Gets bitmap from URI
            uploadImageAsJpeg(bitmap) // Uploads bitmap as JPEG
        }
    }

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        enableEdgeToEdge() // Enables edge-to-edge display
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
        storage = FirebaseStorage.getInstance() // Gets FirebaseStorage instance

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

        // Handle profile image edit button click
        binding.editProfileImageButton.setOnClickListener { // Sets listener for edit profile image button
            if (isNetworkAvailable()) { // Checks network availability
                pickImage.launch("image/*") // Launches image picker
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
            }
        }

        // Handle full name update button click
        binding.editFullNameButton.setOnClickListener { // Sets listener for edit name button
            if (isNetworkAvailable()) { // Checks network availability
                updateName() // Calls updateName method
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
            }
        }

        // Handle email update button click
        binding.editEmailButton.setOnClickListener { // Sets listener for edit email button
            if (isNetworkAvailable()) { // Checks network availability
                updateEmail() // Calls updateEmail method
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
            }
        }

        // Handle change password prompt click
        binding.changePasswordPrompt.setOnClickListener { // Sets listener for change password prompt
             if (isNetworkAvailable()) { // Checks network availability
                showPasswordChangeConfirmation() // Calls showPasswordChangeConfirmation
            } else { // Executed if network is unavailable
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows info toast
            }
        }
        
        // Toggle password visibility
        binding.showPasswordButton.setOnClickListener { // Sets listener for show password button
            val passwordEditText = binding.passwordText // Gets password edit text
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) { // Checks if visible
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD // Hides password
                binding.showPasswordButton.setImageResource(R.drawable.show_password_icon) // Sets show icon
            } else { // Executed if hidden
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD // Shows password
                binding.showPasswordButton.setImageResource(R.drawable.hide_password_icon) // Sets hide icon
            }
            passwordEditText.setSelection(passwordEditText.text.length) // Moves cursor to end
        }

        // Handle sign out button click
        binding.signOutButton.setOnClickListener { // Sets listener for sign out button
            showSignOutConfirmation() // Calls showSignOutConfirmation method
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
    
    // Shows a confirmation dialog before initiating the password change process
    private fun showPasswordChangeConfirmation() { // Defines showPasswordChangeConfirmation method
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates SweetAlertDialog
            .setTitleText("Change Password") // Sets title
            .setContentText("Are You Sure You Want To Change Your Password?") // Sets content text
            .setConfirmText("Yes") // Sets confirm text
            .setConfirmClickListener { sDialog -> // Sets confirm listener
                // Change alert type to progress while sending email
                sDialog.changeAlertType(SweetAlertDialog.PROGRESS_TYPE) // Changes alert type to progress
                sDialog.titleText = "Sending Link..." // Sets title text
                sDialog.setCancelable(false) // Sets not cancelable
                changePassword(sDialog) // Calls changePassword method
            }
            .setCancelText("Back") // Sets cancel text
            .setCancelClickListener { it.dismissWithAnimation() } // Sets cancel listener
            .show() // Shows dialog
    }

    // Compresses the selected image and uploads it to Firebase Storage
    private fun uploadImageAsJpeg(bitmap: Bitmap) { // Defines uploadImageAsJpeg method
        binding.progressBar.visibility = View.VISIBLE // Shows progress bar
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        // Use a unique filename with timestamp to avoid caching issues
        val storageRef = storage.reference.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg") // Creates storage reference
        val baos = ByteArrayOutputStream() // Creates ByteArrayOutputStream
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos) // Compresses bitmap to JPEG
        val data = baos.toByteArray() // Converts stream to byte array

        // Upload the image data
        storageRef.putBytes(data).addOnSuccessListener { // Uploads data, adds success listener
            // Get the download URL upon successful upload
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl -> // Gets download URL
                updateProfileImage(downloadUrl.toString()) // Calls updateProfileImage
            }
        }.addOnFailureListener { // Adds failure listener
            binding.progressBar.visibility = View.GONE // Hides progress bar
            showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows error toast
        }
    }

    // Updates the user's profile image URL in Firebase Auth and Database
    @SuppressLint("UseKtx") // Suppresses lint warning
    private fun updateProfileImage(imageUrl: String) { // Defines updateProfileImage method
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        val userProfileChangeRequest = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(imageUrl)).build() // Builds profile change request
        auth.currentUser?.updateProfile(userProfileChangeRequest) // Updates user profile

        // Update the database record
        val userRef = database.reference.child("users").child(userId) // References user node
        userRef.child("profileImage").setValue(imageUrl).addOnCompleteListener { // Sets profile image value
            binding.progressBar.visibility = View.GONE // Hides progress bar
            if(it.isSuccessful) { // Checks if update was successful
                showToast("Profile Image Updated Successfully", FancyToast.SUCCESS) // Shows success toast
                // Also update the image view immediately
                if (!isDestroyed && !isFinishing) { // Checks activity state
                    Glide.with(this@ProfilePage) // Loads image with Glide
                        .load(imageUrl) // Loads URL
                        .apply(RequestOptions.circleCropTransform()) // Applies circle crop
                        .placeholder(R.drawable.default_avatar) // Sets placeholder
                        .error(R.drawable.default_avatar) // Sets error image
                        .into(binding.profileImage) // Into profile image view
                }
            } else { // Executed if failed
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows error toast
            }
        }
    }

    // Updates the user's display name
    private fun updateName() { // Defines updateName method
        binding.progressBar.visibility = View.VISIBLE // Shows progress bar
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        val newName = binding.profileFullName.text.toString().trim() // Gets new name
        
        // Validate the new name
        if (newName.isEmpty()) { // Checks if name is empty
            binding.progressBar.visibility = View.GONE // Hides progress bar
            showToast("Name cannot be empty", FancyToast.WARNING) // Shows warning toast
            return // Returns
        }

        // Update display name in Firebase Auth
        val userProfileChangeRequest = UserProfileChangeRequest.Builder().setDisplayName(newName).build() // Builds profile change request
        auth.currentUser?.updateProfile(userProfileChangeRequest)?.addOnCompleteListener { task -> // Updates profile
            if (task.isSuccessful) { // Checks if successful
                // Update name in Firebase Database
                val userRef = database.reference.child("users").child(userId) // References user node
                userRef.child("name").setValue(newName).addOnCompleteListener{ // Sets name value
                    binding.progressBar.visibility = View.GONE // Hides progress bar
                    if(it.isSuccessful) { // Checks if successful
                        showToast("Name Updated Successfully", FancyToast.SUCCESS) // Shows success toast
                    } else { // Executed if failed
                        showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows error toast
                    }
                }
            } else { // Executed if auth update failed
                binding.progressBar.visibility = View.GONE // Hides progress bar
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows error toast
            }
        }
    }

    // Updates the user's email address
    private fun updateEmail() { // Defines updateEmail method
        binding.progressBar.visibility = View.VISIBLE // Shows progress bar
        val newEmail = binding.profileEmail.text.toString().trim() // Gets new email
        
        // Validate the new email
        if (newEmail.isEmpty() || newEmail == auth.currentUser?.email) { // Checks if email is invalid or unchanged
            binding.progressBar.visibility = View.GONE // Hides progress bar
            showToast("Please enter a new, valid email", FancyToast.WARNING) // Shows warning toast
            return // Returns
        }

        // Send a verification email before updating
        auth.currentUser?.verifyBeforeUpdateEmail(newEmail)?.addOnCompleteListener { task -> // Sends verification
            binding.progressBar.visibility = View.GONE // Hides progress bar
            if (task.isSuccessful) { // Checks if successful
                showToast("Verification email sent to $newEmail. Please verify to update.", FancyToast.INFO) // Shows info toast
            } else { // Executed if failed
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows error toast
            }
        }
    }

    // Sends a password reset email to the user
    private fun changePassword(dialog: SweetAlertDialog) { // Defines changePassword method
        val user = auth.currentUser // Gets current user
        if (user != null && user.email != null) { // Checks if user and email exist
            auth.sendPasswordResetEmail(user.email!!).addOnCompleteListener { task -> // Sends password reset email
                if (task.isSuccessful) { // Checks if successful
                    dialog.changeAlertType(SweetAlertDialog.SUCCESS_TYPE) // Changes alert type to success
                    dialog.titleText = "Success!" // Sets title
                    dialog.contentText = "Password reset link sent to your email." // Sets content
                    dialog.setConfirmText("OK") // Sets confirm text
                    dialog.setConfirmClickListener { it.dismissWithAnimation() } // Sets confirm listener
                    dialog.show() // Shows dialog
                } else { // Executed if failed
                    dialog.changeAlertType(SweetAlertDialog.ERROR_TYPE) // Changes alert type to error
                    dialog.titleText = "Error" // Sets title
                    dialog.contentText = "Something went wrong. Please try again." // Sets content
                    dialog.setConfirmText("OK") // Sets confirm text
                    dialog.setConfirmClickListener { it.dismissWithAnimation() } // Sets confirm listener
                    dialog.show() // Shows dialog
                }
            }
        } else { // Executed if user or email is null
            dialog.changeAlertType(SweetAlertDialog.ERROR_TYPE) // Changes alert type to error
            dialog.titleText = "Error" // Sets title
            dialog.contentText = "Could not find a registered email to send the link to." // Sets content
            dialog.setConfirmText("OK") // Sets confirm text
            dialog.setConfirmClickListener { it.dismissWithAnimation() } // Sets confirm listener
            dialog.show() // Shows dialog
        }
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
                if (!isDestroyed && !isFinishing) { // Checks activity state
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
