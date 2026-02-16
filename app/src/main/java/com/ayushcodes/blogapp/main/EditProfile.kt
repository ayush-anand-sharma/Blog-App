package com.ayushcodes.blogapp.main // Defines the package for the class.

import android.annotation.SuppressLint // Imports the annotation used to suppress lint warnings.
import android.content.Context // Imports the Context class, which provides access to application-specific resources and classes.
import android.graphics.Bitmap // Imports the Bitmap class, used for representing images.
import android.net.ConnectivityManager // Imports the ConnectivityManager class, used to check network connectivity.
import android.net.NetworkCapabilities // Imports NetworkCapabilities, which provides information about the properties of a network.
import android.net.Uri // Imports the Uri class, used to identify resources.
import android.os.Bundle // Imports the Bundle class, used for passing data between Android components.
import android.provider.MediaStore // Imports the MediaStore class, which provides access to the media content provider.
import android.view.View // Imports the View class, the basic building block for user interface components.
import androidx.activity.enableEdgeToEdge // Imports the function to enable edge-to-edge display in the activity.
import androidx.activity.result.contract.ActivityResultContracts // Imports ActivityResultContracts, which provides standard contracts for activity results.
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity, a base class for activities that use the support library action bar features.
import androidx.core.view.ViewCompat // Imports ViewCompat, which provides compatibility helpers for views.
import androidx.core.view.WindowInsetsCompat // Imports WindowInsetsCompat, which provides compatibility for window insets.
import cn.pedant.SweetAlert.SweetAlertDialog // Imports the SweetAlertDialog library for displaying beautiful alert dialogs.
import com.ayushcodes.blogapp.R // Imports the R class, which contains all the resource IDs for the application.
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

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { // Registers a contract to get content (an image) from the system.
        uri: Uri? -> // A lambda that handles the returned URI of the selected image.
        uri?.let { // Executes the block if the URI is not null.
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it) // Converts the image URI to a Bitmap.
            uploadImageAsJpeg(bitmap) // Calls the function to upload the bitmap as a JPEG image.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method, which is called when the activity is first created.
        super.onCreate(savedInstanceState) // Calls the superclass's implementation of onCreate.
        enableEdgeToEdge() // Enables edge-to-edge display for the activity, allowing content to draw under system bars.

        // If the user is offline, show a toast and finish the activity before setting the content view.
        if (!isNetworkAvailable()) { // Checks if the device has an active network connection.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows an informational toast to the user.
            finish() // Finishes the activity, preventing the user from seeing an empty page.
            return // Stops further execution of the onCreate method.
        }

        binding = ActivityEditProfileBinding.inflate(layoutInflater) // Inflates the layout and initializes the binding object.
        setContentView(binding.root) // Sets the activity's content view to the root of the inflated layout.

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
    }

    private fun setupClickListeners() { // Defines a private function to set up all the click listeners.
        binding.backButton.setOnClickListener { // Sets a click listener on the back button.
            finish() // Closes the current activity and returns to the previous one.
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
                updateName() // Calls the function to update the user's name.
                updateEmail() // Calls the function to update the user's email.
            } else { // Executes if no network is available.
                showToast("Please check your internet connection..", FancyToast.INFO) // Shows a toast message about the internet connection.
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

    private fun uploadImageAsJpeg(bitmap: Bitmap) { // Defines a function to upload a Bitmap image as a JPEG to Firebase Storage.
        binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, or returns if the user is not logged in.
        val storageRef = storage.reference.child("profile_images/${userId}_${System.currentTimeMillis()}.jpg") // Creates a reference in Firebase Storage for the new image.
        val baos = ByteArrayOutputStream() // Creates a new ByteArrayOutputStream to hold the image data.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos) // Compresses the bitmap into JPEG format with 100% quality.
        val data = baos.toByteArray() // Converts the compressed image to a byte array.

        storageRef.putBytes(data).addOnSuccessListener { // Uploads the byte array to Firebase Storage and adds a success listener.
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl -> // On successful upload, gets the download URL for the image.
                updateProfileImage(downloadUrl.toString()) // Calls the function to update the user's profile image URL.
            }
        }.addOnFailureListener { // Adds a failure listener for the upload task.
            binding.progressBar.visibility = View.GONE // Hides the progress bar.
            showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
        }
    }

    @SuppressLint("UseKtx") // Suppresses a lint warning suggesting to use KTX extensions.
    private fun updateProfileImage(imageUrl: String) { // Defines a function to update the user's profile image URL in Firebase Auth and Database.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, returning if null.
        val userProfileChangeRequest = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(imageUrl)).build() // Builds a request to update the user's photo URI.
        auth.currentUser?.updateProfile(userProfileChangeRequest) // Updates the user's profile in Firebase Authentication.

        val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the Firebase Realtime Database.
        userRef.child("profileImage").setValue(imageUrl).addOnCompleteListener { // Sets the new profile image URL in the database.
            binding.progressBar.visibility = View.GONE // Hides the progress bar.
            if (it.isSuccessful) { // Checks if the database update was successful.
                showToast("Profile Image Updated Successfully", FancyToast.SUCCESS) // Shows a success toast message.
                if (!isDestroyed && !isFinishing) { // Checks if the activity is still active.
                    Glide.with(this@EditProfile) // Begins a Glide load request.
                        .load(imageUrl) // Specifies the URL of the image to load.
                        .apply(RequestOptions.circleCropTransform()) // Applies a circular crop transformation to the image.
                        .placeholder(R.drawable.default_avatar) // Sets a placeholder image to display while loading.
                        .error(R.drawable.default_avatar) // Sets an error image to display if the load fails.
                        .into(binding.profileImage) // Sets the target ImageView to display the loaded image.
                }
            } else { // Executes if the database update failed.
                showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
            }
        }
    }

    private fun updateName() { // Defines a function to update the user's display name.
        val userId = auth.currentUser?.uid ?: return // Gets the current user's ID, returns if null.
        val newName = binding.profileFullName.text.toString().trim() // Gets the new name from the EditText and trims whitespace.

        if (newName.isEmpty()) { // Checks if the new name is empty.
            showToast("Name cannot be empty", FancyToast.WARNING) // Shows a warning toast if the name is empty.
            return // Exits the function.
        }

        if (newName != auth.currentUser?.displayName) { // Checks if the new name is different from the current name.
            binding.progressBar.visibility = View.VISIBLE // Shows the progress bar.
            val userProfileChangeRequest = UserProfileChangeRequest.Builder().setDisplayName(newName).build() // Builds a request to update the user's display name.
            auth.currentUser?.updateProfile(userProfileChangeRequest)?.addOnCompleteListener { task -> // Updates the user's profile and adds a completion listener.
                if (task.isSuccessful) { // Checks if the profile update was successful.
                    val userRef = database.reference.child("users").child(userId) // Gets a reference to the user's data in the database.
                    userRef.child("name").setValue(newName).addOnCompleteListener { 
                        binding.progressBar.visibility = View.GONE // Hides the progress bar.
                        if (it.isSuccessful) { // Checks if the database update was successful.
                            showToast("Name Updated Successfully", FancyToast.SUCCESS) // Shows a success toast.
                        } else { // Executes if the database update failed.
                            showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
                        }
                    }
                } else { // Executes if the profile update failed.
                    binding.progressBar.visibility = View.GONE // Hides the progress bar.
                    showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast.
                }
            }
        }
    }

    private fun updateEmail() { // Defines a function to update the user's email address.
        val newEmail = binding.profileEmail.text.toString().trim() // Gets the new email from the EditText and trims whitespace.

        if (newEmail.isEmpty()) { // Checks if the new email is empty.
            showToast("Email cannot be empty", FancyToast.WARNING) // Shows a warning toast if the email is empty.
            return // Exits the function.
        }

        if (newEmail != auth.currentUser?.email) { // Checks if the new email is different from the current one.
            binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
            auth.currentUser?.verifyBeforeUpdateEmail(newEmail)?.addOnCompleteListener { task -> // Sends a verification email for the new email address.
                binding.progressBar.visibility = View.GONE // Hides the progress bar.
                if (task.isSuccessful) { // Checks if sending the verification email was successful.
                    showToast("Verification email sent to $newEmail. Please verify to update.", FancyToast.INFO) // Informs the user that a verification email has been sent.
                } else { // Executes if sending the verification email failed.
                    showToast("Something went wrong. Please try again.", FancyToast.ERROR) // Shows an error toast message.
                }
            }
        }
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
                val email = auth.currentUser?.email // Retrieves the user's email from FirebaseAuth.
                val profileImage = snapshot.child("profileImage").getValue(String::class.java) // Retrieves the user's profile image URL from the snapshot.

                binding.profileFullName.setText(name ?: auth.currentUser?.displayName) // Sets the full name EditText with the retrieved name, or the display name from Auth.
                binding.profileEmail.setText(email) // Sets the email EditText with the user's email.

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
                if (!isDestroyed || isFinishing) { // Checks if the activity is still active.
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
