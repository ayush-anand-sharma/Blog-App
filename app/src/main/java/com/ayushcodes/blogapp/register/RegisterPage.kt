@file:Suppress("DEPRECATION") // Suppresses deprecation warnings for the entire file

package com.ayushcodes.blogapp.register // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Intent // Imports Intent class for launching activities
import android.graphics.Bitmap // Imports Bitmap for image handling
import android.net.Uri // Imports Uri for handling image URIs
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.provider.MediaStore // Imports MediaStore for accessing media content
import android.text.InputType // Imports InputType for configuring EditText input types
import android.view.View // Imports View class for UI elements
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts for handling activity results
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.ActivityRegisterPageBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.HomePage // Imports the HomePage activity
import com.ayushcodes.blogapp.model.UserData // Imports the UserData model class
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.auth.UserProfileChangeRequest // Imports UserProfileChangeRequest for updating user profile
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.storage.FirebaseStorage // Imports FirebaseStorage for accessing Firebase Storage
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream for converting images to bytes
import java.text.SimpleDateFormat // Imports SimpleDateFormat for formatting dates
import java.util.Date // Imports Date class
import java.util.Locale // Imports Locale class

// Activity responsible for handling user registration logic, including Firebase Auth, Realtime Database, and Storage interactions.
class RegisterPage : AppCompatActivity() { // Defines the RegisterPage class inheriting from AppCompatActivity
    // Lazily initialize view binding for the activity layout
    private val binding: ActivityRegisterPageBinding by lazy { // Declares binding variable using lazy initialization
        ActivityRegisterPageBinding.inflate(layoutInflater) // Inflates the layout
    }
    // Firebase service instances
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance
    private lateinit var storage: FirebaseStorage // Declares FirebaseStorage instance
    // Variable to store the selected image URI
    private var imageUri: Uri? = null // Variable to hold the URI of the selected image, initially null

    // Register for activity result to get content (image) from the device storage
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { // Registers activity result launcher for getting content
        uri -> // Lambda parameter for the returned URI
        if (uri != null) { // Checks if the URI is not null
            binding.userProfile.setImageURI(uri) // Sets the selected image to the ImageView
            imageUri = uri // Updates the imageUri variable
        }
    }

    // Called when the activity is starting
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method
        super.onCreate(savedInstanceState) // Calls superclass onCreate
        setContentView(binding.root) // Sets content view using binding

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance
        storage = FirebaseStorage.getInstance() // Gets FirebaseStorage instance

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
            binding.progressBar.visibility = View.VISIBLE // Shows the progress bar
            val name = binding.name.text.toString() // Gets the name text
            val email = binding.email.text.toString() // Gets the email text
            val password = binding.password.text.toString() // Gets the password text

            // Validate that all required fields are filled
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) { // Checks if any field is empty
                binding.progressBar.visibility = View.GONE // Hides the progress bar
                FancyToast.makeText(this, "Please fill all the details", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                return@setOnClickListener // Returns from listener
            }

            // Create a new user with email and password using Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password) // Creates user with email and password
                .addOnCompleteListener(this) { task -> // Adds completion listener
                    if (task.isSuccessful) { // Checks if registration was successful
                        val user = auth.currentUser // Gets the current user
                        user?.let {uid -> // Checks if user is not null
                            // Update the user's profile with the provided display name
                            val profileUpdates = UserProfileChangeRequest.Builder() // Builds profile change request
                                .setDisplayName(name) // Sets the display name
                                .build() // Builds the request
                            user.updateProfile(profileUpdates) // Updates the user profile

                            // Check if a profile image has been selected
                            if (imageUri != null) { // Checks if imageUri is not null
                                // Upload the selected image to Firebase Storage
                                val storageRef = storage.reference.child("profile_images/${user.uid}.jpg") // References the storage path for the image
                                
                                try { // Starts try block for image processing
                                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri) // Gets bitmap from URI
                                    val baos = ByteArrayOutputStream() // Creates ByteArrayOutputStream
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos) // Compresses bitmap to JPEG
                                    val data = baos.toByteArray() // Converts stream to byte array
                                    
                                    storageRef.putBytes(data) // Uploads the byte array to storage
                                        .addOnSuccessListener { // Adds success listener
                                            // Get the download URL of the uploaded image
                                            storageRef.downloadUrl.addOnSuccessListener { uri -> // Gets the download URL
                                                saveUserData(user.uid, name, email, uri.toString()) // Saves user data with image URL
                                            }
                                        }
                                        .addOnFailureListener { // Adds failure listener
                                            binding.progressBar.visibility = View.GONE // Hides the progress bar
                                            FancyToast.makeText(this, "Image Upload Failed", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
                                        }
                                } catch (e: Exception) { // Catches exceptions during bitmap conversion
                                    // Fallback if bitmap conversion fails
                                    storageRef.putFile(imageUri!!) // Uploads the file directly from URI
                                        .addOnSuccessListener { // Adds success listener
                                            storageRef.downloadUrl.addOnSuccessListener { uri -> // Gets the download URL
                                                saveUserData(user.uid, name, email, uri.toString()) // Saves user data with image URL
                                            }
                                        }
                                }
                            } else { // Executed if no image is selected
                                // If no image is selected, save user data with an empty image URL
                                saveUserData(user.uid, name, email, "") // Saves user data with empty image string
                            }
                        }
                    } else { // Executed if registration failed
                        // Handle registration failure
                        binding.progressBar.visibility = View.GONE // Hides the progress bar
                        FancyToast.makeText(this, "Registration Failed: ${task.exception?.message}", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast with message
                    }
                }
        }

        // Navigate to the Log In screen if the user already has an account
        binding.loginHere.setOnClickListener { // Sets OnClickListener for login link
            startActivity(Intent(this, LogInScreen::class.java)) // Starts LogInScreen activity
            finish() // Finishes the current activity
        }
    }
    
    // Save the new user's data to the Firebase Realtime Database
    private fun saveUserData(uid: String, name: String, email: String, profileImageUrl: String) { // Defines function to save user data
        val creationTimestamp = auth.currentUser?.metadata?.creationTimestamp ?: System.currentTimeMillis() // Gets creation timestamp
        val creationDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp)) // Formats creation date
        // Do not save password in database for security
        val userData = UserData(name, email, null, profileImageUrl, creationDate) // Creates UserData object
        
        database.reference.child("users").child(uid).setValue(userData).addOnCompleteListener{ // Sets user data in database
            binding.progressBar.visibility = View.GONE // Hides the progress bar
            if(it.isSuccessful) { // Checks if database write was successful
                FancyToast.makeText(this, "Registration Successful", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS, R.mipmap.blog_app_icon_round, false).show() // Shows success toast
                startActivity(Intent(this, HomePage::class.java)) // Starts HomePage activity
                finishAffinity() // Finishes all activities in the task
            } else { // Executed if database write failed
                FancyToast.makeText(this, "Registration Failed: ${it.exception?.message}", FancyToast.LENGTH_SHORT, FancyToast.ERROR, R.mipmap.blog_app_icon_round, false).show() // Shows error toast
            }
        }
    }
}
