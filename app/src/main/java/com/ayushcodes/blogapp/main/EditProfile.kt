package com.ayushcodes.blogapp.main // Defines the package for the class.

import android.annotation.SuppressLint // Imports the annotation used to suppress lint warnings.
import android.content.Context // Imports the Context class, which provides access to application-specific resources and classes.
import android.content.Intent // Imports Intent class for launching activities
import android.graphics.Bitmap // Imports the Bitmap class, used for representing images.
import android.net.ConnectivityManager // Imports the ConnectivityManager class, used to check network connectivity.
import android.net.NetworkCapabilities // Imports NetworkCapabilities, which provides information about the properties of a network.
import android.net.Uri // Imports the Uri class, used to identify resources.
import android.os.Bundle // Imports the Bundle class, used for passing data between Android components.
import android.provider.MediaStore // Imports the MediaStore class, which provides access to the media content provider.
import android.text.Editable
import android.text.TextWatcher
import android.view.View // Imports the View class, the basic building block for user interface components.
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge // Imports the function to enable edge-to-edge display in the activity.
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts, which provides standard contracts for activity results.
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity, a base class for activities that use the support library action bar features.
import androidx.core.net.toUri
import androidx.core.view.ViewCompat // Imports ViewCompat, which provides compatibility helpers for views.
import androidx.core.view.WindowInsetsCompat // Imports WindowInsetsCompat, which provides compatibility for window insets.
import cn.pedant.SweetAlert.SweetAlertDialog // Imports the SweetAlertDialog library for displaying beautiful alert dialogs.
import com.ayushcodes.blogapp.R // Imports the R class, which contains all the resource IDs for the application.
import com.ayushcodes.blogapp.activities.CropperActivity
import com.ayushcodes.blogapp.databinding.ActivityEditProfileBinding // Imports the generated data binding class for the activity_edit_profile layout.
import com.bumptech.glide.Glide // Imports the Glide library for image loading and caching.
import com.bumptech.glide.request.RequestOptions // Imports RequestOptions, used to apply options to a Glide request.
import com.google.firebase.auth.FirebaseAuth // Imports the FirebaseAuth class, used for handling user authentication.
import com.google.firebase.auth.UserProfileChangeRequest // Imports UserProfileChangeRequest, used to update a user's profile information.
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot, which represents a snapshot of the data at a Firebase Database location.
import com.google.firebase.database.DatabaseError // Imports DatabaseError, which represents an error that occurred while listening to a Firebase Database location.
import com.google.firebase.database.FirebaseDatabase // Imports the FirebaseDatabase class, the entry point for accessing a Firebase Database.
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener, used to receive events about data changes at a location.
import com.google.firebase.storage.FirebaseStorage // Imports the FirebaseStorage class, the entry point for accessing Firebase Storage.
import com.shashank.sony.fancytoastlib.FancyToast // Imports the FancyToast library for creating custom toasts.
import java.io.ByteArrayOutputStream // Imports ByteArrayOutputStream, which implements an output stream in which the data is written into a byte array.

@Suppress("DEPRECATION") // Suppresses warnings for deprecated code used in this class.
class EditProfile : AppCompatActivity() { // Defines the EditProfile class, which inherits from AppCompatActivity.

    private lateinit var binding: ActivityEditProfileBinding // Declares a late-initialized variable for the view binding object.
    private lateinit var auth: FirebaseAuth // Declares a late-initialized variable for the FirebaseAuth instance.
    private lateinit var database: FirebaseDatabase // Declares a late-initialized variable for the FirebaseDatabase instance.
    private lateinit var storage: FirebaseStorage // Declares a late-initialized variable for the FirebaseStorage instance.

    private var newImageUri: Uri? = null // Holds the URI of the newly selected profile image.
    private var isNameChanged = false // Flag to track if the user has changed their name.
    private var isProfileImageChanged = false // Flag to track if the user has selected a new profile image.
    private var initialName: String? = null // Stores the initial name of the user to compare against changes.

    // Register for activity result to get content (image) from the device storage
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> // Registers activity result launcher for getting content
        uri?.let { // Checks if the URI is not null
            val intent = Intent(this, CropperActivity::class.java) // Create an Intent to start CropperActivity
            intent.data = it // Set the image URI as data for the intent
            cropImage.launch(intent) // Launch the CropperActivity to crop the image
        }
    }

    // Register for activity result to get the cropped image
    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> // Registers activity result launcher for getting cropped image
        if (result.resultCode == RESULT_OK) { // Check if the result is OK
            val uri = result.data?.data // Get the cropped image URI from the result
            uri?.let { // If the URI is not null
                newImageUri = it // Stores the selected image URI.
                isProfileImageChanged = true // Sets the flag to indicate that the profile image has been changed.
                binding.profileImage.setImageURI(it) // Sets the cropped image to the ImageView
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method, which is called when the activity is first created.
        super.onCreate(savedInstanceState) // Calls the superclass's implementation of onCreate.
        enableEdgeToEdge() // Enables edge-to-edge display for the activity, allowing content to draw under system bars.
        binding = ActivityEditProfileBinding.inflate(layoutInflater) // Inflates the layout and initializes the binding object.
        setContentView(binding.root) // Sets the activity's content view to the root of the inflated layout.

        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty page.
            return // Stops further execution of the onCreate method.
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets -> // Sets a listener to handle window insets, such as the status and navigation bars.
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()) // Retrieves the insets for the system bars.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom) // Applies padding to the root view to avoid content overlapping with system bars.
            insets // Returns the original insets.
        }

        auth = FirebaseAuth.getInstance() // Gets an instance of FirebaseAuth.
        database = FirebaseDatabase.getInstance() // Gets an instance of FirebaseDatabase.
        storage = FirebaseStorage.getInstance() // Gets an instance of FirebaseStorage.

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
        if (newImageUri != null) { // Checks if a new image has been selected.
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, newImageUri) // Converts the new image URI to a Bitmap.
            uploadImageAndUpdateProfile(bitmap, progressDialog) // Uploads the new image and updates the profile.
        } else if (isNameChanged) { // Checks if only the name has been changed.
            updateName(progressDialog) // Updates the name.
        }
    }

    private fun uploadImageAndUpdateProfile(bitmap: Bitmap, progressDialog: SweetAlertDialog) { // Defines a function to upload the image and then update the profile.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, or returns if the user is not logged in.
        val storageRef = storage.reference.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg") // Creates a reference in Firebase Storage for the new image.
        val baos = ByteArrayOutputStream() // Creates a new ByteArrayOutputStream to hold the image data.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos) // Compresses the bitmap into JPEG format with 100% quality.
        val data = baos.toByteArray() // Converts the compressed image to a byte array.

        storageRef.putBytes(data).addOnSuccessListener { // Uploads the byte array to Firebase Storage and adds a success listener.
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl -> // On successful upload, gets the download URL for the image.
                updateProfileImage(downloadUrl.toString(), progressDialog) // Calls the function to update the user's profile image URL.
            }
        }.addOnFailureListener { // Adds a failure listener for the upload task.
            progressDialog.dismissWithAnimation()
            showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
        }
    }

    private fun updateProfileImage(imageUrl: String, progressDialog: SweetAlertDialog) { // Defines a function to update the user's profile image URL in Firebase Auth and Database.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, returning if null.
        val userProfileChangeRequest = UserProfileChangeRequest.Builder().setPhotoUri(imageUrl.toUri()).build() // Builds a request to update the user's photo URI using the KTX extension.
        auth.currentUser?.updateProfile(userProfileChangeRequest) // Updates the user's profile in Firebase Authentication.

        val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the Firebase Realtime Database.
        userRef.child("profileImage").setValue(imageUrl).addOnCompleteListener { // Sets the new profile image URL in the database.
            if (it.isSuccessful) { // Checks if the database update was successful.
                isProfileImageChanged = false // Resets the profile image changed flag.
                if (isNameChanged) { // Checks if the name was also changed.
                    updateName(progressDialog) // Calls the function to update the name.
                } else { // If only the image was changed.
                    progressDialog.dismissWithAnimation()
                    showToast("Profile Saved", FancyToast.SUCCESS) // Shows a success toast message.
                }
            } else { // Executes if the database update failed.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
            }
        }
    }

    private fun updateName(progressDialog: SweetAlertDialog) { // Defines a function to update the user's display name.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, returns if null.
        val newName = binding.profileFullName.text.toString().trim() // Gets the new name from the EditText and trims whitespace.

        if (newName.isEmpty()) { // Checks if the new name is empty.
            showToast("Name cannot be empty", FancyToast.WARNING) // Shows a warning toast if the name is empty.
            progressDialog.dismissWithAnimation()
            return // Exits the function.
        }

        val userProfileChangeRequest = UserProfileChangeRequest.Builder().setDisplayName(newName).build() // Builds a request to update the user's display name.
        auth.currentUser?.updateProfile(userProfileChangeRequest)?.addOnCompleteListener { task -> // Updates the user's profile and adds a completion listener.
            if (task.isSuccessful) { // Checks if the profile update was successful.
                val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the database.
                userRef.child("name").setValue(newName).addOnCompleteListener { // Updates the name in the database.
                    progressDialog.dismissWithAnimation()
                    if (it.isSuccessful) { // Checks if the database update was successful.
                        isNameChanged = false // Resets the name changed flag.
                        showToast("Profile Saved", FancyToast.SUCCESS) // Shows a success toast.
                        initialName = newName // Updates the initial name to the new name.
                    } else { // Executes if the database update failed.
                        showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
                    }
                }
            } else { // Executes if the profile update failed.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
            }
        }
    }

    private fun showPasswordChangeConfirmation() { // Defines a function to show a confirmation dialog before changing the password.
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // Creates a new warning-style SweetAlertDialog.
            .setTitleText("Change Password") // Sets the title of the alert dialog.
            .setContentText("Are You Sure You Want To Change Your Password?") // Sets the main message of the alert dialog.
            .setConfirmText("Yes") // Sets the text for the confirm button.
            .setConfirmClickListener { sDialog -> // Sets the action to perform when the confirm button is clicked.
                sDialog.changeAlertType(SweetAlertDialog.PROGRESS_TYPE) // Changes the dialog to a progress type.
                sDialog.titleText = "Sending Link..." // Updates the title to indicate progress.
                sDialog.setCancelable(false) // Prevents the dialog from being canceled by the user.
                changePassword(sDialog) // Calls the function to send the password reset email.
            }
            .setCancelText("Back") // Sets the text for the cancel button.
            .setCancelClickListener { it.dismissWithAnimation() } // Sets the action to dismiss the dialog when the cancel button is clicked.
            .show() // Displays the alert dialog.
    }

    private fun changePassword(dialog: SweetAlertDialog) { // Defines a function to handle the password change process.
        val user = auth.currentUser // Gets the currently logged-in user.
        if (user != null && user.email != null) { // Checks if the user and their email are not null.
            auth.sendPasswordResetEmail(user.email!!).addOnCompleteListener { task -> // Sends a password reset email to the user's email address.
                if (task.isSuccessful) { // Checks if the email was sent successfully.
                    dialog.changeAlertType(SweetAlertDialog.SUCCESS_TYPE) // Changes the dialog to a success type.
                    dialog.titleText = "Success!" // Sets the title of the success dialog.
                    dialog.contentText = "Password reset link sent to your email." // Sets the content of the success dialog.
                    dialog.setConfirmText("OK") // Sets the confirm button text.
                    dialog.setConfirmClickListener { it.dismissWithAnimation() } // Dismisses the dialog on confirm click.
                    dialog.show() // Shows the updated dialog.
                } else { // Executes if sending the email failed.
                    dialog.changeAlertType(SweetAlertDialog.ERROR_TYPE) // Changes the dialog to an error type.
                    dialog.titleText = "Error" // Sets the title of the error dialog.
                    dialog.contentText = "Something went wrong. Please try again." // Sets the content of the error dialog.
                    dialog.setConfirmText("OK") // Sets the confirm button text.
                    dialog.setConfirmClickListener { it.dismissWithAnimation() } // Dismisses the dialog on confirm click.
                    dialog.show() // Shows the updated dialog.
                }
            }
        } else { // Executes if the user or their email is null.
            dialog.changeAlertType(SweetAlertDialog.ERROR_TYPE) // Changes the dialog to an error type.
            dialog.titleText = "Error" // Sets the title of the error dialog.
            dialog.contentText = "Could not find a registered email to send the link to." // Sets the content of the error dialog.
            dialog.setConfirmText("OK") // Sets the confirm button text.
            dialog.setConfirmClickListener { it.dismissWithAnimation() } // Dismisses the dialog on confirm click.
            dialog.show() // Shows the updated dialog.
        }
    }

    private fun loadUserData() { // Defines a function to load user data from Firebase.
        binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, or returns if null.
        val userReference = database.reference.child("users").child(userId) // Gets a reference to the user's data in the database.

        userReference.addListenerForSingleValueEvent(object : ValueEventListener { // Adds a one-time listener to fetch the user's data.
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data is retrieved from Firebase.
                if (isDestroyed || isFinishing) return // Returns if the activity is being destroyed or finishing.

                val name = snapshot.child("name").getValue(String::class.java) // Retrieves the user's name from the data snapshot.
                val profileImage = snapshot.child("profileImage").getValue(String::class.java) // Retrieves the user's profile image URL from the snapshot.

                initialName = name ?: auth.currentUser?.displayName // Sets the initial name for later comparison.
                binding.profileFullName.setText(initialName) // Sets the full name EditText with the retrieved name.

                if (!profileImage.isNullOrEmpty()) { // Checks if a profile image URL was retrieved.
                    Glide.with(this@EditProfile) // Starts a Glide request.
                        .load(profileImage) // Loads the image from the URL.
                        .apply(RequestOptions.circleCropTransform()) // Applies a circular crop.
                        .placeholder(R.drawable.default_avatar) // Sets a placeholder image.
                        .error(R.drawable.default_avatar) // Sets an error image.
                        .into(binding.profileImage) // Displays the image in the profile ImageView.
                } else { // Executes if no profile image URL is in the database.
                    auth.currentUser?.photoUrl?.let { // Checks if the user has a photo URL in their Firebase Auth profile.
                        Glide.with(this@EditProfile) // Starts a Glide request.
                            .load(it) // Loads the image from the Auth photo URL.
                            .apply(RequestOptions.circleCropTransform()) // Applies a circular crop.
                            .placeholder(R.drawable.default_avatar) // Sets a placeholder.
                            .error(R.drawable.default_avatar) // Sets an error image.
                            .into(binding.profileImage) // Displays the image.
                    } ?: Glide.with(this@EditProfile) // If no Auth photo URL, loads the default avatar.
                        .load(R.drawable.default_avatar) // Loads the default avatar drawable.
                        .apply(RequestOptions.circleCropTransform()) // Applies a circular crop.
                        .into(binding.profileImage) // Displays the default avatar.
                }
                binding.progressBar.visibility = View.GONE // Hides the progress bar.
            }

            override fun onCancelled(error: DatabaseError) { // Called if the database read is cancelled or fails.
                if (!isDestroyed || !isFinishing) { // Checks if the activity is still active.
                    binding.progressBar.visibility = View.GONE // Hides the progress bar.
                    showToast("Something went wrong.", FancyToast.ERROR) // Shows an error toast.
                }
            }
        })
    }

    private fun isNetworkAvailable(): Boolean { // Defines a function to check for network availability.
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager system service.
        val network = connectivityManager.activeNetwork ?: return false // Gets the currently active network, or returns false if none.
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets the capabilities of the active network, or returns false if none.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Returns true if the network has WiFi transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Or cellular transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Or ethernet transport.
    }

    private fun showToast(message: String, type: Int) { // Defines a function to show a custom toast message.
        if (!isDestroyed && !isFinishing) { // Checks if the activity is not being destroyed or finishing.
            FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast.
        }
    }
}
