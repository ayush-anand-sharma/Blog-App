@file:Suppress("DEPRECATION") // Suppresses deprecation warnings for the entire file

package com.ayushcodes.blogapp.register // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context
import android.content.Intent // Imports Intent class for launching activities
import android.graphics.Bitmap // Imports Bitmap class for image manipulation
import android.graphics.drawable.Drawable // Imports Drawable class for image resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.text.InputType // Imports InputType for configuring EditText input types
import android.view.View // Imports View class for UI elements
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts for handling activity results
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import androidx.lifecycle.lifecycleScope
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.ActivityLogInScreenBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.HomePage // Imports the HomePage activity
import com.ayushcodes.blogapp.repository.UserRepository // Import the UserRepository to handle user data operations.
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.bumptech.glide.request.target.CustomTarget // Imports CustomTarget for Glide callbacks
import com.bumptech.glide.request.transition.Transition // Imports Transition for Glide animations
import com.google.android.gms.auth.api.signin.GoogleSignIn // Imports GoogleSignIn class
import com.google.android.gms.auth.api.signin.GoogleSignInClient // Imports GoogleSignInClient class
import com.google.android.gms.auth.api.signin.GoogleSignInOptions // Imports GoogleSignInOptions class
import com.google.android.gms.common.api.ApiException // Imports ApiException class for Google API errors
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.auth.FirebaseUser // Imports FirebaseUser for user details
import com.google.firebase.auth.GoogleAuthProvider // Imports GoogleAuthProvider for Google credential handling
import com.google.firebase.storage.FirebaseStorage // Imports FirebaseStorage for accessing Firebase Storage
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import kotlinx.coroutines.launch // Import for launching coroutines.
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream for converting images to bytes

// Activity to handle user login functionality, including email/password and Google Sign-In.
class LogInScreen : AppCompatActivity() { // Defines the LogInScreen class inheriting from AppCompatActivity
    // Lazily initialize ViewBinding for the activity layout.
    private val binding: ActivityLogInScreenBinding by lazy { // Declares binding variable using lazy initialization
        ActivityLogInScreenBinding.inflate(layoutInflater) // Inflates the layout
    }

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance

    // Google Sign-In client.
    private lateinit var googleSignInClient: GoogleSignInClient // Declares GoogleSignInClient instance

    private lateinit var userRepository: UserRepository // A repository to handle user data.

    // Firebase Storage instance.
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance

    // Called when the activity is first created.
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances.
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        userRepository = UserRepository() // Initialize the user repository.
        storage = FirebaseStorage.getInstance() // Gets FirebaseStorage instance

        // Set click listener for the back button to finish the activity.
        binding.backButton.setOnClickListener { // Sets OnClickListener for back button
            finish() // Finishes the activity
        }

        // Configure Google Sign-In options.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN) // Builds GoogleSignInOptions
            .requestIdToken(getString(R.string.default_web_client_id)) // Requests ID token using web client ID
            .requestEmail() // Requests user email
            .build() // Builds the options
        googleSignInClient = GoogleSignIn.getClient(this, gso) // Gets the GoogleSignInClient

        // Toggle password visibility when the show/hide icon is clicked.
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

        // Handle login button click.
        binding.loginButton.setOnClickListener { // Sets OnClickListener for login button
            if (isNetworkAvailable()) { // Checks for a network connection.
                binding.progressBar.visibility = View.VISIBLE // Shows the progress bar
                val email = binding.email.text.toString() // Gets the email text
                val password = binding.password.text.toString() // Gets the password text

                // Validate email and password inputs.
                if (email.isEmpty() || password.isEmpty()) { // Checks if email or password is empty
                    binding.progressBar.visibility = View.GONE // Hides the progress bar
                    FancyToast.makeText(this, "Please fill all the details", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                    return@setOnClickListener // Returns from listener
                }

                // Attempt to sign in with email and password using Firebase Auth.
                auth.signInWithEmailAndPassword(email, password) // Signs in with email and password
                    .addOnCompleteListener(this) { task -> // Adds completion listener
                        if (isFinishing || isDestroyed) return@addOnCompleteListener // Don't proceed if the activity is no longer active.
                        binding.progressBar.visibility = View.GONE // Hides the progress bar
                        if (task.isSuccessful) { // Checks if sign-in was successful
                            FancyToast.makeText(this, "Login Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Shows success toast
                            startActivity(Intent(this, HomePage::class.java)) // Starts HomePage activity
                            finishAffinity() // Finishes all activities in the task
                        } else { // Executed if sign-in failed
                            FancyToast.makeText(this, "Your Email Or Password is Incorrect..", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                        }
                    }
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Handle Google Sign-In button click.
        binding.googleButton.setOnClickListener { // Sets OnClickListener for Google sign-in button
            if (isNetworkAvailable()) { // Checks for a network connection.
                binding.progressBar.visibility = View.VISIBLE // Shows the progress bar
                val signInIntent = googleSignInClient.signInIntent // Gets the sign-in intent
                launcher.launch(signInIntent) // Launches the sign-in intent
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Navigate to the Registration page.
        binding.registerHere.setOnClickListener { // Sets OnClickListener for register link
            startActivity(Intent(this, RegisterPage::class.java)) // Starts RegisterPage activity
            finish() // Finishes the current activity
        }

        // Navigate to the Forgot Password page.
        binding.forgotPassword.setOnClickListener { // Sets OnClickListener for forgot password link
            if (isNetworkAvailable()) { // Checks for a network connection.
                startActivity(Intent(this, ForgotPassword::class.java)) // Starts ForgotPassword activity
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser // Get the current user from Firebase Authentication.
        if (currentUser != null) { // If a user is currently signed in.
            startActivity(Intent(this, HomePage::class.java)) // Navigate to the HomePage.
            finish() // Finish WelcomeScreen to prevent user from going back to it.
        }
    }

    // Register a callback for the Google Sign-In activity result.
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> // Registers activity result callback
        if (result.resultCode == RESULT_OK) { // Checks if result is OK
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data) // Gets signed-in account task
            try { // Starts try block for authentication
                // Authenticate with Firebase using the Google account credential.
                val account = task.getResult(ApiException::class.java) // Gets the GoogleSignInAccount
                val credential = GoogleAuthProvider.getCredential(account.idToken, null) // Gets Firebase credential from Google ID token
                auth.signInWithCredential(credential) // Signs in to Firebase with credential
                    .addOnCompleteListener { // Adds completion listener
                        if (isFinishing || isDestroyed) return@addOnCompleteListener // Don't proceed if the activity is no longer active.
                        if (it.isSuccessful) { // Checks if sign-in was successful
                            val user = auth.currentUser // Gets the current Firebase user
                            user?.let { firebaseUser -> // Checks if user is not null
                                val userName = firebaseUser.displayName ?: "Anonymous" // Provide a default name if the display name is null.
                                val userEmail = firebaseUser.email ?: "" // Provide an empty string if the email is null.
                                val googlePhotoUrl = firebaseUser.photoUrl?.toString() ?: "" // Gets photo URL or empty string
                                // 1. Save data and Login immediately with Google URL
                                saveUserData(firebaseUser, userName, userEmail, googlePhotoUrl) // Saves user data
                                // 2. Trigger background upload to Storage
                                if (googlePhotoUrl.isNotEmpty()) { // We only need to upload if there is an image
                                    uploadGoogleImageToStorage(firebaseUser, userName, userEmail, googlePhotoUrl) // Uploads image in background
                                }
                            }
                        } else { // Executed if sign-in failed
                            handleSignInFailure() // Handles sign-in failure
                        }
                    }
            } catch (e: ApiException) { // Catches ApiException
                handleSignInFailure() // Handles sign-in failure
            }
        } else { // Executed if result is not OK
            handleSignInFailure() // Handles sign-in failure
        }
    }

    // Uploads the user's Google profile image to Firebase Storage in background
    private fun uploadGoogleImageToStorage(user: FirebaseUser, userName: String, userEmail: String, photoUrl: String) { // Defines function to upload image
        if (photoUrl.isEmpty()) return // Returns if URL is empty

        // Use applicationContext to prevent issues if activity finishes
        Glide.with(applicationContext) // Initializes Glide with application context
            .asBitmap() // Requests a Bitmap
            .load(photoUrl) // Loads the photo URL
            .into(object : CustomTarget<Bitmap>() { // Loads into a CustomTarget
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) { // Called when resource is ready
                    val baos = ByteArrayOutputStream() // Creates ByteArrayOutputStream
                    resource.compress(Bitmap.CompressFormat.JPEG, 100, baos) // Compresses bitmap to JPEG
                    val data = baos.toByteArray() // Converts stream to byte array

                    val storageRef = storage.reference.child("profile_images/${user.uid}.jpg") // References storage path
                    storageRef.putBytes(data) // Uploads data to storage
                        .addOnSuccessListener { // Adds success listener
                            storageRef.downloadUrl.addOnSuccessListener { downloadUrl -> // Gets download URL on success
                                lifecycleScope.launch { // Launch a coroutine in the lifecycle scope.
                                    if (isFinishing || isDestroyed) return@launch // Don't proceed if the activity is no longer active.
                                    userRepository.saveUserProfile(user.uid, userName, userEmail, downloadUrl.toString()) // Silently update the profile image in the database
                                }
                            }
                        }
                }

                override fun onLoadCleared(placeholder: Drawable?) { } // Called when load is cleared

                override fun onLoadFailed(errorDrawable: Drawable?) { } // Called when load fails
            })
    }

    // Save user data to Firebase Realtime Database.
    private fun saveUserData(user: FirebaseUser, userName: String, userEmail: String, imageUrl: String) { // Defines function to save user data
        lifecycleScope.launch { // Launch a coroutine in the lifecycle scope.
            try { // Start a try-catch block for error handling.
                userRepository.saveUserProfile(user.uid, userName, userEmail, imageUrl) // Save the user profile using the repository.
                if (isFinishing || isDestroyed) return@launch // Don't proceed if the activity is no longer active.
                binding.progressBar.visibility = View.GONE // Hide the progress bar on successful registration.
                FancyToast.makeText(this@LogInScreen, "Login Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Show a success message to the user.
                startActivity(Intent(this@LogInScreen, HomePage::class.java)) // Navigate to the home page.
                finishAffinity() // Finish all activities in the current task.
            } catch (e: Exception) { // Catch any exceptions that occur during the process.
                if (isFinishing || isDestroyed) return@launch // Don't proceed if the activity is no longer active.
                handleSignInFailure() // Handles sign-in failure
            }
        }
    }

    // Handle failures during the sign-in process.
    private fun handleSignInFailure() { // Defines failure handling function
        if (isFinishing || isDestroyed) return // Don't proceed if the activity is no longer active.
        binding.progressBar.visibility = View.GONE // Hides progress bar
        FancyToast.makeText(this, "Google Sign-In Failed", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
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
