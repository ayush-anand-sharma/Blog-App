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
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.ActivityLogInScreenBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.HomePage // Imports the HomePage activity
import com.ayushcodes.blogapp.model.UserData // Imports the UserData model class
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
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.storage.FirebaseStorage // Imports FirebaseStorage for accessing Firebase Storage
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream for converting images to bytes
import java.text.SimpleDateFormat // Imports SimpleDateFormat for formatting dates
import java.util.Date // Imports Date class
import java.util.Locale // Imports Locale class

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
    
    // Firebase Realtime Database instance.
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Firebase Storage instance.
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance

    // Called when the activity is first created.
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances.
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance
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

    // Register a callback for the Google Sign-In activity result.
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { // Registers activity result callback
        result -> // Lambda parameter for result
        if (result.resultCode == RESULT_OK) { // Checks if result is OK
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data) // Gets signed-in account task
            try { // Starts try block for authentication
                // Authenticate with Firebase using the Google account credential.
                val account = task.getResult(ApiException::class.java) // Gets the GoogleSignInAccount
                val credential = GoogleAuthProvider.getCredential(account.idToken, null) // Gets Firebase credential from Google ID token
                auth.signInWithCredential(credential) // Signs in to Firebase with credential
                    .addOnCompleteListener { // Adds completion listener
                        if (it.isSuccessful) { // Checks if sign-in was successful
                            val user = auth.currentUser // Gets the current Firebase user
                            user?.let { firebaseUser -> // Checks if user is not null
                                // Directly save user data without re-uploading the image to speed up login
                                val photoUrl = firebaseUser.photoUrl?.toString() ?: "" // Gets photo URL or empty string
                                // 1. Save data and Login immediately with Google URL
                                saveUserData(firebaseUser, photoUrl) // Saves user data
                                // 2. Trigger background upload to Storage
                                uploadGoogleImageToStorage(firebaseUser, photoUrl) // Uploads image in background
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
    private fun uploadGoogleImageToStorage(user: FirebaseUser, photoUrl: String) { // Defines function to upload image
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
                                // Silently update the profile image in the database
                                database.reference.child("users").child(user.uid).child("profileImage").setValue(downloadUrl.toString()) // Updates profile image URL in DB
                            }
                        }
                }

                override fun onLoadCleared(placeholder: Drawable?) { } // Called when load is cleared

                override fun onLoadFailed(errorDrawable: Drawable?) { } // Called when load fails
            })
    }

    // Save user data to Firebase Realtime Database.
    private fun saveUserData(user: FirebaseUser, imageUrl: String) { // Defines function to save user data
        val creationTimestamp = user.metadata?.creationTimestamp ?: System.currentTimeMillis() // Gets creation timestamp
        val creationDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp)) // Formats creation date
        
        // FIX: Use updateChildren instead of setValue to preserve "Blogs" node and other sub-nodes
        val userUpdates = mapOf(
            "name" to user.displayName,
            "email" to user.email,
            "profileImage" to imageUrl,
            "creationDate" to creationDate
        )

        database.reference.child("users").child(user.uid).updateChildren(userUpdates).addOnCompleteListener { task -> // Sets user data in DB
            binding.progressBar.visibility = View.GONE // Hides progress bar
            if (task.isSuccessful) { // Checks if successful
                FancyToast.makeText(this, "Login Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Shows success toast
                startActivity(Intent(this, HomePage::class.java)) // Starts HomePage activity
                finishAffinity() // Finishes all activities
            } else { // Executed if failed
                handleSignInFailure() // Handles failure
            }
        }
    }

    // Handle failures during the sign-in process.
    private fun handleSignInFailure() { // Defines failure handling function
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
