@file:Suppress("DEPRECATION") // Suppresses deprecation warnings for the entire file

package com.ayushcodes.blogapp.register // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ayushcodes.blogapp.R
import com.ayushcodes.blogapp.databinding.ActivityWelcomeScreenBinding
import com.ayushcodes.blogapp.main.HomePage
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.shashank.sony.fancytoastlib.FancyToast
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Activity to handle the Welcome screen and Google Sign-In
class WelcomeScreen : AppCompatActivity() { // Defines the WelcomeScreen class inheriting from AppCompatActivity
    // Lazily initialize the view binding for the activity
    private val binding: ActivityWelcomeScreenBinding by lazy { // Declares binding variable using lazy initialization
        ActivityWelcomeScreenBinding.inflate(layoutInflater) // Inflates the layout
    }
    
    // Firebase Authentication instance
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    
    // Google Sign-In Client
    private lateinit var googleSignInClient: GoogleSignInClient // Declares GoogleSignInClient instance
    
    // Firebase Realtime Database instance
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance
    
    // Firebase Storage instance
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance

    // Called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance
        storage = FirebaseStorage.getInstance() // Gets FirebaseStorage instance

        // Configure Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN) // Builds GoogleSignInOptions
            .requestIdToken(getString(R.string.default_web_client_id)) // Requests ID token using web client ID
            .requestEmail() // Requests user email
            .build() // Builds the options
        googleSignInClient = GoogleSignIn.getClient(this, gso) // Gets the GoogleSignInClient

        // Set up click listener for Google Sign-In button
        binding.googleButton.setOnClickListener { // Sets OnClickListener for Google sign-in button
            if (isNetworkAvailable()) { // Checks for a network connection.
                binding.progressBar.visibility = View.VISIBLE // Shows the progress bar
                val signInIntent = googleSignInClient.signInIntent // Gets the sign-in intent
                launcher.launch(signInIntent) // Launches the sign-in intent
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Set up click listener for Email Login button
        binding.loginEmail.setOnClickListener { // Sets OnClickListener for email login button
            if (isNetworkAvailable()) { // Checks for a network connection.
                startActivity(Intent(this, LogInScreen::class.java)) // Starts LogInScreen activity
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Set up click listener for Register button
        binding.registerEmail.setOnClickListener { // Sets OnClickListener for register button
            if (isNetworkAvailable()) { // Checks for a network connection.
                startActivity(Intent(this, RegisterPage::class.java)) // Starts RegisterPage activity
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
            }
        }

        // Handle back button press to show exit confirmation dialog
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { // Adds a callback for back button press
            override fun handleOnBackPressed() { // Overrides the handleOnBackPressed method
                SweetAlertDialog(this@WelcomeScreen, SweetAlertDialog.WARNING_TYPE) // Creates a SweetAlertDialog with warning type
                    .setTitleText("Exit App") // Sets the title of the dialog
                    .setContentText("Are you sure you want to exit?") // Sets the content text of the dialog
                    .setConfirmText("Exit") // Sets the text for the confirm button
                    .setConfirmClickListener { // Sets the OnClickListener for the confirm button
                        finishAffinity() // Finishes the activity and all parent activities
                    }
                    .setCancelText("Back") // Sets the text for the cancel button
                    .setCancelClickListener { sDialog -> // Sets the OnClickListener for the cancel button
                        sDialog.dismissWithAnimation() // Dismisses the dialog with animation
                    }
                    .show() // Shows the dialog
            }
        })
    }

    // Activity Result Launcher for Google Sign-In intent
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { // Registers activity result callback
        result -> // Lambda parameter for result
        if (result.resultCode == RESULT_OK) { // Checks if result is OK
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data) // Gets signed-in account task
            try { // Starts try block for authentication
                // Authenticate with Firebase using the Google account
                val account = task.getResult(ApiException::class.java) // Gets the GoogleSignInAccount
                val credential = GoogleAuthProvider.getCredential(account.idToken, null) // Gets Firebase credential from Google ID token
                auth.signInWithCredential(credential) // Signs in to Firebase with credential
                    .addOnCompleteListener { // Adds completion listener
                        if (it.isSuccessful) { // Checks if sign-in was successful
                            val user = auth.currentUser // Gets the current Firebase user
                            user?.let { firebaseUser -> // Checks if user is not null
                                val googlePhotoUrl = firebaseUser.photoUrl?.toString() ?: "" // Gets photo URL or empty string
                                // 1. Save data and Login immediately with Google URL
                                saveUserData(firebaseUser, googlePhotoUrl) // Saves user data
                                // 2. Trigger background upload to Storage
                                uploadGoogleImageToStorage(firebaseUser, googlePhotoUrl) // Uploads image in background
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

    // Saves the user's data to Firebase Realtime Database
    private fun saveUserData(user: FirebaseUser, imageUrl: String) { // Defines function to save user data
        val creationTimestamp = user.metadata?.creationTimestamp ?: System.currentTimeMillis() // Gets creation timestamp
        val creationDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp)) // Formats creation date
        
        // FIX: Use updateChildren instead of setValue to preserve "Blogs" node and other sub-nodes
        val userUpdates = mapOf( // Creates a map for updates
            "name" to user.displayName, // Sets display name
            "email" to user.email, // Sets email
            "profileImage" to imageUrl, // Sets profile image
            "creationDate" to creationDate // Sets creation date
        ) // Ends map creation

        database.reference.child("users").child(user.uid).updateChildren(userUpdates).addOnCompleteListener { task -> // Sets user data in DB using updateChildren
            binding.progressBar.visibility = View.GONE // Hides progress bar
            if (task.isSuccessful) { // Checks if successful
                FancyToast.makeText(this, "Login Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Shows success toast
                startActivity(Intent(this, HomePage::class.java)) // Starts HomePage activity
                finishAffinity() // Finishes all activities in the task
            } else { // Executed if failed
                handleSignInFailure() // Handles failure
            }
        }
    }

    // Handles sign-in failures by hiding the progress bar and showing an error toast
    private fun handleSignInFailure() { // Defines failure handling function
        binding.progressBar.visibility = View.GONE // Hides progress bar
        FancyToast.makeText(this, "Google Sign-In Failed", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
    }

    // Checks if the user is already signed in on start
    override fun onStart() { // Overrides onStart method
        super.onStart() // Calls superclass onStart
        if (auth.currentUser != null) { // Checks if current user is not null
            startActivity(Intent(this, HomePage::class.java)) // Starts HomePage activity
            finish() // Finishes the current activity
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
