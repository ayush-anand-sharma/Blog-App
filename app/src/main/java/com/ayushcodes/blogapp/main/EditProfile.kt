package com.ayushcodes.blogapp.main // Defines the package for the class.

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ayushcodes.blogapp.databinding.ActivityEditProfileBinding
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.shashank.sony.fancytoastlib.FancyToast
import com.yalantis.ucrop.UCrop // Imports the uCrop library for image cropping.
import java.io.ByteArrayOutputStream
import java.io.File // Imports the File class for creating a destination for the cropped image.

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
        userRef.child("profileImage").setValue(imageUrl).addOnCompleteListener { task -> // Sets the new profile image URL in the database.
            if (task.isSuccessful) { // Checks if the database update was successful.
                updateBlogPostsWithNewProfileImage(userId, imageUrl, progressDialog) // Updates all blog posts with the new profile image.
                isProfileImageChanged = false // Resets the profile image changed flag.
                if (isNameChanged) { // Checks if the name was also changed.
                    updateName(progressDialog) // Calls the function to update the name.
                } else { // If only the image was changed.
                    progressDialog.dismissWithAnimation()// Dismiss the progress dialog.
                    showToast("Profile Saved", FancyToast.SUCCESS) // Shows a success toast message.
                    navigateToProfilePage() // Navigates to the ProfilePage.
                }
                oldImageUrl?.let { url -> // Checks if there is an old image URL.
                    storage.getReferenceFromUrl(url).delete() // Deletes the old profile picture from Firebase Storage.
                }
            } else { // Executes if the database update failed.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
            }
        }
    }

    private fun updateName(progressDialog: SweetAlertDialog) { // Defines a function to update the user's name in Firebase.
        val newName = binding.profileFullName.text.toString().trim() // Gets the new name from the EditText and trims any whitespace.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, or returns if null.

        // Update display name in Firebase Authentication.
        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build() // Builds a request to update the user's display name.
        auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener { task -> // Updates the user's profile in Firebase Authentication.
            if (task.isSuccessful) { // Checks if the update was successful.
                val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the database.
                userRef.child("name").setValue(newName).addOnCompleteListener { task2 ->// Sets the new name in the database.
                    if (task2.isSuccessful) { // Checks if the database update was successful.
                        updateBlogPostsWithNewName(userId, newName, progressDialog) // Updates all blog posts with the new name.
                        isNameChanged = false // Resets the name changed flag.
                        showToast("Profile Saved", FancyToast.SUCCESS) // Shows a success toast.
                        navigateToProfilePage() // Navigates to the ProfilePage.
                    } else { // If the database update failed.
                        progressDialog.dismissWithAnimation()
                        showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
                    }
                }
            } else { // If the auth update failed.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
            }
        }
    }

    private fun updateBlogPostsWithNewName(userId: String, newName: String, progressDialog: SweetAlertDialog) { // Defines a function to update all of a user's blog posts with the new name.
        val blogsRef = database.reference.child("blogs") // Gets a reference to the main "blogs" node in the database.
        blogsRef.orderByChild("userId").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener { // Queries for all blogs where the userId matches the current user's ID.
            override fun onDataChange(snapshot: DataSnapshot) { // Called when the data is retrieved.
                for (blogSnapshot in snapshot.children) { // Iterates through all the matching blog posts.
                    blogSnapshot.ref.child("userName").setValue(newName) // Updates the userName for each blog post.
                }
                progressDialog.dismissWithAnimation()
            }

            override fun onCancelled(error: DatabaseError) { // Called if the query is cancelled or fails.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
            }
        })
    }

    private fun updateBlogPostsWithNewProfileImage(userId: String, newImageUrl: String, progressDialog: SweetAlertDialog) { // Defines a function to update all of a user's blog posts with the new profile image.
        val blogsRef = database.reference.child("blogs") // Gets a reference to the main "blogs" node in the database.
        blogsRef.orderByChild("userId").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener { // Queries for all blogs where the userId matches the current user's ID.
            override fun onDataChange(snapshot: DataSnapshot) { // Called when the data is retrieved.
                for (blogSnapshot in snapshot.children) { // Iterates through all the matching blog posts.
                    blogSnapshot.ref.child("profileImage").setValue(newImageUrl) // Updates the profileImage for each blog post.
                }
            }

            override fun onCancelled(error: DatabaseError) { // Called if the query is cancelled or fails.
                progressDialog.dismissWithAnimation()
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
            }
        })
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
                        Glide.with(this@EditProfile).load(oldImageUrl).into(binding.profileImage) // Loads the profile image using Glide.

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
        FancyToast.makeText(this, message, FancyToast.LENGTH_SHORT, type, com.ayushcodes.blogapp.R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast.
    }

    private fun navigateToProfilePage() { // Defines a function to navigate to the ProfilePage.
        val intent = Intent(this, ProfilePage::class.java) // Creates an intent to go to the ProfilePage.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // This flag ensures that the ProfilePage is launched in a new task.
        startActivity(intent) // Starts the ProfilePage activity.
        finish() // Finishes the current activity.
    }
}