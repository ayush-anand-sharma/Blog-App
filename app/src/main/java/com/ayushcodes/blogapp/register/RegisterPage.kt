@file:Suppress("DEPRECATION") // Suppresses deprecation warnings for the entire file

package com.ayushcodes.blogapp.register // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context
import android.content.Intent // Imports Intent class for launching activities
import android.graphics.Bitmap // Imports Bitmap for image handling
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri // Imports Uri for handling image URIs
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.provider.MediaStore // Imports MediaStore for accessing media content
import android.text.InputType // Imports InputType for configuring EditText input types
import android.view.View // Imports View class for UI elements
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts for handling activity results
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import androidx.lifecycle.lifecycleScope
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.ActivityRegisterPageBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.HomePage // Imports the HomePage activity
import com.ayushcodes.blogapp.repository.UserRepository // Import the UserRepository to handle user data operations.
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest // Imports UserProfileChangeRequest for updating user profile
import com.google.firebase.storage.FirebaseStorage // Imports FirebaseStorage for accessing Firebase Storage
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import com.yalantis.ucrop.UCrop // Imports the uCrop library for image cropping.
import kotlinx.coroutines.launch // Import for launching coroutines.
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream for converting images to bytes
import java.io.File // Imports the File class for creating a destination for the cropped image.

// Activity responsible for handling user registration logic, including Firebase Auth, Realtime Database, and Storage interactions.
class RegisterPage : AppCompatActivity() { // Defines the RegisterPage class inheriting from AppCompatActivity
    private lateinit var binding: ActivityRegisterPageBinding // Declares binding variable
    // Firebase service instances
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance
    private lateinit var userRepository: UserRepository // A repository to handle user data.
    // Variable to store the selected image URI
    private var imageUri: Uri? = null // Variable to hold the URI of the selected image, initially null

    // Register for activity result to get content (image) from the device storage
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> // Registers a launcher to pick an image from the device's storage.
        uri?.let { // Executes the block if an image URI is successfully retrieved.
            val destinationUri = Uri.fromFile(File(cacheDir, "IMG_" + System.currentTimeMillis())) // Creates a destination URI for the cropped image in the cache directory.
            val options = UCrop.Options() // Creates a new set of options for customizing the uCrop experience.
            options.setCircleDimmedLayer(true) // Sets the cropping overlay to a circular shape.
            options.setCompressionQuality(90) // Sets the compression quality for the cropped image to 90.
            options.setShowCropFrame(true) // Makes the crop frame visible.
            options.setShowCropGrid(true) // Makes the crop grid visible.

            val uCrop = UCrop.of(it, destinationUri) // Creates a uCrop request with the source and destination URIs.
                .withOptions(options) // Applies the custom options.
                .withAspectRatio(1f, 1f) // Sets a fixed 1:1 aspect ratio for the crop.
                .withMaxResultSize(1000, 1000) // Sets the maximum dimensions of the cropped image.

            cropImage.launch(uCrop.getIntent(this)) // Launches the uCrop activity using the configured intent.
        }
    }

    // Register for activity result to get the cropped image
    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> // Registers a launcher to handle the result from the uCrop activity.
        if (result.resultCode == RESULT_OK) { // Checks if the cropping was successful.
            val uri = UCrop.getOutput(result.data!!) // Retrieves the URI of the cropped image from the result data.
            uri?.let { // Executes the block if the cropped image URI is not null.
                binding.userProfile.setImageURI(it) // Displays the cropped image in the profile image view.
                imageUri = it // Updates the imageUri with the cropped image's URI.
            }
        }
    }

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        binding = ActivityRegisterPageBinding.inflate(layoutInflater) // Inflates the layout
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        storage = FirebaseStorage.getInstance() // Gets FirebaseStorage instance
        userRepository = UserRepository() // Initialize the user repository.

        // Set click listener for the back button to finish the activity
        binding.backButton.setOnClickListener { // Sets OnClickListener for back button
            finish() // Finishes the activity
        }

        // Toggle password visibility when the show/hide icon is clicked
        binding.showPassword.setOnClickListener { // Sets OnClickListener for show/hide password button
            val passwordEditText = binding.password // Gets the password EditText
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) { // Checks if password is visible
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD // Hides the password
                binding.showPassword.setImageResource(R.drawable.show_password_icon) // Sets the icon to "show"
            } else { // Executed if password is hidden
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD // Shows the password
                binding.showPassword.setImageResource(R.drawable.hide_password_icon) // Sets the icon to "hide"
            }
            passwordEditText.setSelection(passwordEditText.text.length) // Moves cursor to the end of the text
        }

        // Launch image picker when the profile image card is clicked
        binding.cardView.setOnClickListener { // Sets OnClickListener for the card view wrapping the profile image
            pickImage.launch("image/*") // Launches the image picker for all image types
        }

        // Handle user registration when the register button is clicked
        binding.registerButton.setOnClickListener { // Sets OnClickListener for the register button
            if (isNetworkAvailable()) { // Checks for a network connection.
                binding.progressBar.visibility = View.VISIBLE // Shows the progress bar
                val name = binding.name.text.toString().trim() // Gets the name text
                val email = binding.email.text.toString().trim() // Gets the email text
                val password = binding.password.text.toString().trim() // Gets the password text

                // Validate that all required fields are filled
                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) { // Checks if any field is empty
                    binding.progressBar.visibility = View.GONE // Hides the progress bar
                    FancyToast.makeText(this, "Please fill all the details", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                    return@setOnClickListener // Returns from listener
                }

                // Create a new user with email and password using Firebase Authentication
                auth.createUserWithEmailAndPassword(email, password) // Creates user with email and password
                    .addOnCompleteListener(this) { task -> // Adds completion listener
                        if (isFinishing || isDestroyed) return@addOnCompleteListener // Don't proceed if the activity is no longer active.
                        if (task.isSuccessful) { // Checks if registration was successful
                            val user = auth.currentUser // Gets the current user
                            if (user == null) { // If the user is null, something went wrong.
                                binding.progressBar.visibility = View.GONE // Hide the progress bar.
                                FancyToast.makeText(this, "Registration Failed: User not found.", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Show an error toast.
                                return@addOnCompleteListener // Return from the listener.
                            }
                            updateUserProfileAndSaveData(user, name, email) // Update the user profile and save the data.
                        } else { // Executed if registration failed
                            // Handle registration failure
                            binding.progressBar.visibility = View.GONE // Hides the progress bar
                            FancyToast.makeText(this, "Registration Failed: ${task.exception?.message}", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                        }
                    }
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Navigate to the Log In screen if the user already has an account
        binding.loginHere.setOnClickListener { // Sets OnClickListener for login link
            if (isNetworkAvailable()) { // Checks for a network connection.
                startActivity(Intent(this, LogInScreen::class.java)) // Starts LogInScreen activity
                finish() // Finishes the current activity
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }
    }

    private fun updateUserProfileAndSaveData(user: FirebaseUser, name: String, email: String) { // Defines a function to update the user profile and save the data.
        val profileUpdates = UserProfileChangeRequest.Builder() // Builds a profile change request.
            .setDisplayName(name) // Sets the display name.
            .build() // Builds the request.

        user.updateProfile(profileUpdates).addOnCompleteListener { // Updates the user profile.
            if (isFinishing || isDestroyed) return@addOnCompleteListener // Don't proceed if the activity is no longer active.

            if (imageUri != null) { // Checks if an image has been selected.
                uploadImageAndSaveUserData(user.uid, name, email) // Upload the image and save the user data.
            } else { // If no image has been selected.
                saveUserData(user.uid, name, email, "") // Save the user data with an empty image URL.
            }
        }
    }

    private fun uploadImageAndSaveUserData(userId: String, name: String, email: String) { // Defines a function to upload the image and save the user data.
        val storageRef = storage.reference.child("profile_images/${userId}.jpg") // Creates a reference to the user's profile picture in Firebase Storage.

        imageUri?.let { uri -> // Executes this block if imageUri is not null.
            storageRef.putFile(uri) // Uploads the file from the URI.
                .addOnSuccessListener { // Adds a success listener for the upload.
                    if (isFinishing || isDestroyed) return@addOnSuccessListener // Don't proceed if the activity is no longer active.
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri -> // Gets the download URL for the uploaded file.
                        if (isFinishing || isDestroyed) return@addOnSuccessListener // Don't proceed if the activity is no longer active.
                        saveUserData(userId, name, email, downloadUri.toString()) // Saves the user data with the new image URL.
                    }
                }
                .addOnFailureListener { // Adds a failure listener for the upload.
                    if (isFinishing || isDestroyed) return@addOnFailureListener // Don't proceed if the activity is no longer active.
                    binding.progressBar.visibility = View.GONE // Hides the progress bar.
                    FancyToast.makeText(this, "Image Upload Failed", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows an error toast.
                }
        }
    }

    // Save the new user's data to the Firebase Realtime Database
    private fun saveUserData(uid: String, name: String, email: String, profileImageUrl: String) { // Defines function to save user data
        lifecycleScope.launch { // Launch a coroutine in the lifecycle scope.
            try { // Start a try-catch block for error handling.
                userRepository.saveUserProfile(uid, name, email, profileImageUrl) // Save the user profile using the repository.
                if (isFinishing || isDestroyed) return@launch // Don't proceed if the activity is no longer active.
                binding.progressBar.visibility = View.GONE // Hide the progress bar on successful registration.
                FancyToast.makeText(this@RegisterPage, "Registration Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Show a success message to the user.
                startActivity(Intent(this@RegisterPage, HomePage::class.java)) // Navigate to the home page.
                finishAffinity() // Finish all activities in the current task.
            } catch (e: Exception) { // Catch any exceptions that occur during the process.
                if (isFinishing || isDestroyed) return@launch // Don't proceed if the activity is no longer active.
                binding.progressBar.visibility = View.GONE // Hide the progress bar on failure.
                FancyToast.makeText(this@RegisterPage, "Registration Failed: ${e.message}", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Show an error message with the exception details.
            }
        }
    }

    // Checks for network connectivity.
    private fun isNetworkAvailable(): Boolean { // Defines the isNetworkAvailable method.
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the connectivity manager system service.
        val network = connectivityManager.activeNetwork ?: return false // Gets the currently active network, or returns false if none.
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets the capabilities of the active network, or returns false if none.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Returns true if the network has WiFi transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Or cellular transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Or ethernet transport.
    }

    // Shows a custom toast message.
    private fun showToast(message: String, type: Int) { // Defines the showToast method.
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast.
    }
}
