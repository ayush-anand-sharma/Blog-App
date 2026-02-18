package com.ayushcodes.blogapp.main // Defines the package for the class.

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ayushcodes.blogapp.R
import com.ayushcodes.blogapp.databinding.ActivityEditProfileBinding
import com.ayushcodes.blogapp.repository.UserRepository
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.shashank.sony.fancytoastlib.FancyToast
import com.yalantis.ucrop.UCrop // Imports the uCrop library for image cropping.
import java.io.File // Imports the File class for creating a destination for the cropped image.

@Suppress("DEPRECATION") // Suppresses warnings for deprecated code used in this class.
class EditProfile : AppCompatActivity() { // Defines the EditProfile class, which inherits from AppCompatActivity.

    private lateinit var binding: ActivityEditProfileBinding // Declares a late-initialized variable for the view binding object.
    private lateinit var auth: FirebaseAuth // Declares a late-initialized variable for the FirebaseAuth instance.
    private lateinit var database: FirebaseDatabase // Declares a late-initialized variable for the FirebaseDatabase instance.
    private lateinit var userRepository: UserRepository // A repository to handle user data.

    private var newImageUri: Uri? = null // Holds the URI of the newly selected profile image.
    private var isNameChanged = false // Flag to track if the user has changed their name.
    private var isProfileImageChanged = false // Flag to track if the user has selected a new profile image.
    private var initialName: String? = null // Stores the initial name of the user to compare against changes.
    private var oldImageUrl: String? = null // Stores the old image URL to delete it from storage.

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

    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> // Registers a launcher to handle the result from the uCrop activity.
        if (result.resultCode == RESULT_OK) { // Checks if the cropping was successful.
            val uri = UCrop.getOutput(result.data!!) // Retrieves the URI of the cropped image from the result data.
            uri?.let { // Executes the block if the cropped image URI is not null.
                newImageUri = it // Updates the newImageUri with the cropped image's URI.
                isProfileImageChanged = true // Sets the flag to indicate the profile image has changed.
                binding.profileImage.setImageURI(it) // Displays the cropped image in the profile image view.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method, which is called when the activity is first created.
        super.onCreate(savedInstanceState) // Calls the superclass's implementation of onCreate.
        binding = ActivityEditProfileBinding.inflate(layoutInflater) // Inflates the layout and initializes the binding object.
        setContentView(binding.root) // Sets the activity's content view to the root of the inflated layout.

        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty page.
            return // Stops further execution of the onCreate method.
        }

        auth = FirebaseAuth.getInstance() // Gets an instance of FirebaseAuth.
        database = FirebaseDatabase.getInstance() // Gets an instance of FirebaseDatabase.
        userRepository = UserRepository() // Initialize the user repository.

        loadUserData() // Calls the function to load the user's data from Firebase.
        setupClickListeners() // Calls the function to set up click listeners for the UI elements.
        setupTextWatcher() // Sets up a TextWatcher to monitor changes in the name field.
        setupOnBackPressed() // Sets up the custom back button behavior.
    }

    private fun setupOnBackPressed() { // Defines a function to set up custom handling for the back button press.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { // Adds a callback to the OnBackPressedDispatcher.
            override fun handleOnBackPressed() { // Overrides the method to handle the back press event.
                handleBackButton() // Calls the function to check for unsaved changes before finishing the activity.
            }
        })
    }

    private fun handleBackButton() { // Defines a function to handle the back button logic.
        if (isNameChanged || isProfileImageChanged) { // Checks if there are any unsaved changes.
            showDiscardChangesDialog() // Shows a dialog to confirm discarding changes.
        } else { // If there are no changes.
            finish() // Finishes the activity.
        }
    }

    private fun showDiscardChangesDialog() { // Defines a function to show a confirmation dialog for discarding changes.
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates a new warning-style SweetAlertDialog.
            .setTitleText("Discard Changes?") // Sets the title of the alert dialog.
            .setContentText("You have unsaved changes. Are you sure you want to discard them?") // Sets the main message of the alert dialog.
            .setConfirmText("Yes") // Sets the text for the confirm button.
            .setConfirmClickListener { // Sets the action to perform when the confirm button is clicked.
                it.dismissWithAnimation() // Dismisses the dialog.
                finish() // Finishes the activity, discarding the changes.
            }
            .setCancelText("No") // Sets the text for the cancel button.
            .setCancelClickListener { it.dismissWithAnimation() } // Dismisses the dialog when the cancel button is clicked, keeping the user on the page.
            .show() // Displays the alert dialog.
    }


    private fun setupTextWatcher() { // Defines a function to set up a TextWatcher on the name EditText.
        binding.profileFullName.addTextChangedListener(object : TextWatcher { // Adds a TextWatcher to the profile name EditText.
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} // Called before the text is changed.

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { // Called when the text is changing.
                isNameChanged = s.toString() != initialName // Sets the isNameChanged flag if the current text is different from the initial name.
            }

            override fun afterTextChanged(s: Editable?) {} // Called after the text has changed.
        })
    }

    private fun setupClickListeners() { // Defines a private function to set up all the click listeners.
        binding.backButton.setOnClickListener { // Sets a click listener on the back button.
            handleBackButton() // Calls the function to handle the back button press, checking for unsaved changes.
        }

        binding.editProfileImageButton.setOnClickListener { // Sets a click listener on the button to edit the profile image.
            if (isNetworkAvailable()) { // Checks if there is an active internet connection.
                pickImage.launch("image/*") // Launches the image picker to select an image of any type.
            } else { // Executes if no network is available.
                showToast("Please check your internet connection..", FancyToast.INFO) // Displays a toast message asking the user to check their internet.
            }
        }

        binding.changePasswordPrompt.setOnClickListener { // Sets a click listener on the text view that prompts for a password change.
            if (isNetworkAvailable()) { // Checks for an internet connection.
                showPasswordChangeConfirmation() // Shows a confirmation dialog for changing the password.
            } else { // Executes if no network is available.
                showToast("Please check your internet connection..", FancyToast.INFO) // Displays a toast message about the lack of internet.
            }
        }

        binding.saveProfileButton.setOnClickListener { // Sets a click listener on the button to save the profile.
            if (isNetworkAvailable()) { // Checks if the network is available.
                if (isNameChanged || isProfileImageChanged) { // Checks if there are any changes to be saved.
                    showSaveConfirmationDialog() // Shows a confirmation dialog before saving the changes.
                } else { // If there are no changes.
                    showToast("No changes to save.", FancyToast.INFO) // Informs the user that there are no changes to save.
                }
            } else { // Executes if no network is available.
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message about the internet connection.
            }
        }
    }

    private fun showSaveConfirmationDialog() { // Defines a function to show a confirmation dialog for saving changes.
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates a new warning-style SweetAlertDialog.
            .setTitleText("Save Changes?") // Sets the title of the alert dialog.
            .setContentText("Are you sure you want to save the changes?") // Sets the main message of the alert dialog.
            .setConfirmText("Yes") // Sets the text for the confirm button.
            .setConfirmClickListener { // Sets the action to perform when the confirm button is clicked.
                it.dismissWithAnimation() // Dismisses the dialog.
                val progressDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE) // Creates a new progress-style SweetAlertDialog.
                    .setTitleText("Saving Profile...") // Sets the title of the progress dialog.
                progressDialog.setCancelable(false)
                progressDialog.show() // Displays the progress dialog.
                saveProfile(progressDialog) // Calls the function to save the profile data.
            }
            .setCancelText("No") // Sets the text for the cancel button.
            .setCancelClickListener { it.dismissWithAnimation() } // Dismisses the dialog when the cancel button is clicked.
            .show() // Displays the alert dialog.
    }

    private fun saveProfile(progressDialog: SweetAlertDialog) { // Defines a function to save the user's profile.
        val newName = if (isNameChanged) binding.profileFullName.text.toString().trim() else null // Get the new name if it has changed.
        val userId = auth.currentUser?.uid ?: return // Get the current user's ID, or return if the user is not logged in.

        userRepository.updateUserProfile(userId, newName, newImageUri, oldImageUrl) { success, message -> // Call the updateUserProfile function in the UserRepository.
            progressDialog.dismissWithAnimation() // Dismiss the progress dialog.
            if (success) { // If the update was successful.
                showToast(message, FancyToast.SUCCESS) // Show a success toast message.
                val intent = Intent(this, ProfilePage::class.java) // Creates an Intent to navigate to the ProfilePage screen.
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) // Ensures that if ProfilePage is already in the stack, it's brought to the front without creating a new instance.
                startActivity(intent) // Starts the ProfilePage activity.
                finish() // Finishes the EditProfile activity to remove it from the back stack.
            } else { // If the update failed.
                showToast(message, FancyToast.ERROR) // Show an error toast message.
            }
        }
    }

    private fun showPasswordChangeConfirmation() { // Defines a function to show a confirmation dialog for changing the password.
        SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE) // Creates a new normal-style SweetAlertDialog.
            .setTitleText("Change Password") // Sets the title of the alert dialog.
            .setContentText("You will be logged out and asked to enter your registered email to receive a password reset link.") // Sets the main message of the alert dialog.
            .setConfirmText("Proceed") // Sets the text for the confirm button.
            .setConfirmClickListener { // Sets the action to perform when the confirm button is clicked.
                it.dismissWithAnimation() // Dismisses the dialog.
                sendPasswordResetEmail() // Calls the function to send a password reset email.
            }
            .setCancelText("Back") // Sets the text for the cancel button.
            .setCancelClickListener { it.dismissWithAnimation() } // Dismisses the dialog when the cancel button is clicked.
            .show() // Displays the alert dialog.
    }

    private fun sendPasswordResetEmail() { // Defines a function to send a password reset email.
        val email = auth.currentUser?.email // Gets the current user's email.
        if (email != null) { // Checks if the email is not null.
            auth.sendPasswordResetEmail(email).addOnCompleteListener { task -> // Sends a password reset email to the user's email address.
                if (task.isSuccessful) { // Checks if the email was sent successfully.
                    showToast("Password reset link sent. Check your email.", FancyToast.SUCCESS) // Shows a success toast.
                    auth.signOut() // Signs out the user.
                    val intent = Intent(this, com.ayushcodes.blogapp.register.LogInScreen::class.java) // Creates an intent to go to the login screen.
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clears the activity stack.
                    startActivity(intent) // Starts the login activity.
                    finish() // Finishes the current activity.
                } else { // Executes if the email could not be sent.
                    showToast("Something went wrong.", FancyToast.ERROR) // Shows an error toast.
                }
            }
        } else { // Executes if the user's email is not available.
            showToast("Email not found.", FancyToast.ERROR) // Shows an error toast.
        }
    }

    private fun loadUserData() { // Defines a function to load the user's data.
        val userId = auth.currentUser?.uid // Gets the current user's ID.
        if (userId != null) { // Checks if the user ID is not null.
            val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the database.
            binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
            userRef.addListenerForSingleValueEvent(object : ValueEventListener { // Adds a listener to retrieve the data once.
                override fun onDataChange(snapshot: DataSnapshot) { // Called when the data is retrieved.
                    initialName = snapshot.child("name").getValue(String::class.java) // Gets the user's name from the snapshot.
                    oldImageUrl = snapshot.child("profileImage").getValue(String::class.java) // Gets the user's old profile image URL.

                    binding.profileFullName.setText(initialName) // Sets the user's name in the EditText.

                    if (!isDestroyed) // Checks if the activity is not destroyed before loading the image.
                        Glide.with(this@EditProfile).load(oldImageUrl).placeholder(R.drawable.default_avatar).error(R.drawable.default_avatar).into(binding.profileImage) // Loads the profile image using Glide.

                    binding.progressBar.visibility = View.GONE // Hides the progress bar.
                }

                override fun onCancelled(error: DatabaseError) { // Called if the data retrieval is cancelled.
                    binding.progressBar.visibility = View.GONE // Hides the progress bar.
                    showToast("Failed to load user data", FancyToast.ERROR) // Shows an error toast.
                }
            })
        }
    }

    private fun isNetworkAvailable(): Boolean { // Defines a function to check if there is an active network connection.
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager system service.
        val network = connectivityManager.activeNetwork ?: return false // Gets the current active network.
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets the network capabilities of the active network.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks if the network has Wi-Fi transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks if the network has cellular transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks if the network has Ethernet transport.
    }

    private fun showToast(message: String, type: Int) { // Defines a function to show a custom toast message.
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast.
    }
}