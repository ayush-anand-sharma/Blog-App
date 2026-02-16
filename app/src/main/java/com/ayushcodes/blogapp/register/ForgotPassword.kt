package com.ayushcodes.blogapp.register // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle // Imports Bundle for passing data between Android components
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge for edge-to-edge display
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.ActivityForgotPasswordBinding // Imports the generated binding class for the layout
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException // Imports exception for invalid credentials
import com.google.firebase.auth.FirebaseAuthInvalidUserException // Imports exception for invalid user
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages

// Activity responsible for handling the forgot password functionality.
class ForgotPassword : AppCompatActivity() { // Defines the ForgotPassword class inheriting from AppCompatActivity
    // Lazily initialize view binding for the activity layout
    private val binding: ActivityForgotPasswordBinding by lazy { // Declares binding variable using lazy initialization
        ActivityForgotPasswordBinding.inflate(layoutInflater) // Inflates the layout
    }
    
    // Firebase Authentication instance for password reset operations
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        enableEdgeToEdge() // Enables edge-to-edge display
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance

        // Set click listener for the back button to close the current activity
        binding.backButton.setOnClickListener { // Sets OnClickListener for back button
            finish() // Finishes the activity
        }

        // Set click listener for the submit button to initiate the password reset process
        binding.submitButton.setOnClickListener { // Sets OnClickListener for submit button
            if (isNetworkAvailable()) { // Checks for a network connection.
                val mail = binding.passwordResetEmail.text.toString().trim() // Gets trimmed email from EditText

                // Validate that the email field is not empty
                if (mail.isEmpty()) { // Checks if email is empty
                    FancyToast.makeText(this, "Please Enter Your Email", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                    return@setOnClickListener // Returns from listener
                }
                // Send a password reset email to the provided address
                auth.sendPasswordResetEmail(mail) // Sends password reset email
                    .addOnSuccessListener { // Adds success listener
                        FancyToast.makeText(this, "Reset Link Sent To Your Email...", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Shows success toast
                    }
                    .addOnFailureListener { exception -> // Adds failure listener
                        // Handle failures during the password reset email sending process
                        when (exception) { // Switches on exception type
                            is FirebaseAuthInvalidUserException -> { // Case for invalid user
                                FancyToast.makeText(this, "User or email doesn\'t exist..", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                            }
                            is FirebaseAuthInvalidCredentialsException -> { // Case for invalid credentials
                                FancyToast.makeText(this, "Your Email is Incorrect", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                            }
                            else -> { // Default case
                                FancyToast.makeText(this, "Something Went Wrong: ${exception.message}", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows generic error toast
                            }
                        }
                    }
            } else { // If offline.
                showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message.
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
