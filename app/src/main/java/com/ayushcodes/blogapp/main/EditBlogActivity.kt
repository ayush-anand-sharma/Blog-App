package com.ayushcodes.blogapp.main // Defines the package name for this file

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ayushcodes.blogapp.R
import com.ayushcodes.blogapp.databinding.ActivityEditBlogBinding
import com.ayushcodes.blogapp.model.BlogItemModel
import com.google.firebase.database.FirebaseDatabase
import com.shashank.sony.fancytoastlib.FancyToast

@Suppress("DEPRECATION")
class EditBlogActivity : AppCompatActivity() { // Defines the EditBlogActivity class, which inherits from AppCompatActivity.

    private val binding: ActivityEditBlogBinding by lazy { // Lazily initializes the view binding for the activity.
        ActivityEditBlogBinding.inflate(layoutInflater) // Inflates the layout and creates the binding object.
    }

    private val database = FirebaseDatabase.getInstance() // Gets an instance of the Firebase Realtime Database.
    private lateinit var blogItem: BlogItemModel // Declares a lateinit variable for the blog item being edited.

    private var initialTitle: String = "" // EDITED: Stores the initial title of the blog post to detect changes.
    private var initialDescription: String = "" // EDITED: Stores the initial description of the blog post to detect changes.

    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method, which is called when the activity is first created.
        super.onCreate(savedInstanceState) // Calls the superclass's onCreate method.
        setContentView(binding.root) // Sets the content view of the activity to the root of the binding.

        if (!isNetworkAvailable()) { // Checks if a network connection is available.
            showToast("Please check your internet connection.", FancyToast.INFO) // Shows a toast message if there is no network connection.
            finish() // Finishes the activity if there is no network connection.
            return // Returns from the method.
        }

        blogItem = intent.getParcelableExtra<BlogItemModel>("blogItem")!! // Retrieves the blog item from the intent extras.

        initialTitle = blogItem.heading ?: "" // EDITED: Assigns the original heading to the initialTitle variable.
        initialDescription = blogItem.blogPost ?: "" // EDITED: Assigns the original post content to the initialDescription variable.

        binding.blogTitle.setText(initialTitle) // EDITED: Sets the EditText with the initial title.
        binding.blogDescription.setText(initialDescription) // EDITED: Sets the EditText with the initial description.

        binding.backButton.setOnClickListener { // EDITED: Sets a click listener on the back button.
            handleBackButtonPress() // EDITED: Calls the custom back press handler to check for changes.
        }

        binding.saveBlogButton.setOnClickListener { // Sets a click listener on the save blog button.
            updateBlog() // Calls the updateBlog method to save the changes.
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { // EDITED: Adds a callback for handling the system back button press.
            override fun handleOnBackPressed() { // EDITED: Overrides the method to handle the back press event.
                handleBackButtonPress() // EDITED: Calls the custom back press handler to check for changes.
            } // EDITED: Closes the handleOnBackPressed method.
        }) // EDITED: Closes the addCallback block.
    }

    private fun handleBackButtonPress() { // EDITED: Defines a method to handle back navigation consistently.
        val newTitle = binding.blogTitle.text.toString().trim() // EDITED: Gets the current title from the EditText.
        val newDescription = binding.blogDescription.text.toString().trim() // EDITED: Gets the current description from the EditText.

        if (newTitle != initialTitle || newDescription != initialDescription) { // EDITED: Checks if the content has been changed.
            showDiscardChangesConfirmationDialog() // EDITED: Shows a confirmation dialog if there are unsaved changes.
        } else { // EDITED: If there are no changes.
            finish() // EDITED: Finishes the activity without showing a dialog.
        } // EDITED: Closes the if-else block.
    } // EDITED: Closes the handleBackButtonPress method.

    private fun showDiscardChangesConfirmationDialog() { // EDITED: Defines a method to show the discard confirmation dialog.
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE) // EDITED: Creates a new warning-type SweetAlertDialog.
            .setTitleText("Discard Changes?") // EDITED: Sets the title of the dialog.
            .setContentText("You sure want to discard changes?") // EDITED: Sets the content message of the dialog.
            .setConfirmText("Discard") // EDITED: Sets the text for the confirm button.
            .setConfirmClickListener { sDialog -> // EDITED: Sets a listener for the confirm button click.
                sDialog.dismissWithAnimation() // EDITED: Dismisses the dialog with an animation.
                finish() // EDITED: Finishes the activity, discarding the changes.
            } // EDITED: Closes the confirm click listener block.
            .setCancelText("Cancel") // EDITED: Sets the text for the cancel button.
            .setCancelClickListener { sDialog -> // EDITED: Sets a listener for the cancel button click.
                sDialog.dismissWithAnimation() // EDITED: Dismisses the dialog and keeps the user on the current screen.
            } // EDITED: Closes the cancel click listener block.
            .show() // EDITED: Displays the configured dialog.
    } // EDITED: Closes the showDiscardChangesConfirmationDialog method.


    private fun updateBlog() { // Defines the method to update the blog post.
        val newTitle = binding.blogTitle.text.toString().trim() // Gets the new title from the EditText and trims whitespace.
        val newDescription = binding.blogDescription.text.toString().trim() // Gets the new description from the EditText and trims whitespace.

        if (newTitle.isEmpty() || newDescription.isEmpty()) { // Checks if the new title or description is empty.
            showToast("Please fill all the fields", FancyToast.WARNING) // Shows a warning toast if either field is empty.
            return // Returns from the method.
        }

        binding.progressBar.visibility = View.VISIBLE // Makes the progress bar visible.
        binding.saveBlogButton.isEnabled = false // Disables the save button to prevent multiple clicks.

        val blogReference = database.getReference("blogs").child(blogItem.blogId!!) // Gets a reference to the blog post in the "blogs" node.
        val userBlogReference = database.getReference("users").child(blogItem.userId!!).child("Blogs").child(blogItem.blogId!!) // Gets a reference to the blog post in the user's "Blogs" node.

        val updatedData = mapOf( // Creates a map of the data to be updated.
            "heading" to newTitle, // Maps the "heading" key to the new title.
            "blogPost" to newDescription // Maps the "blogPost" key to the new description.
        )

        blogReference.updateChildren(updatedData) // Updates the blog post in the "blogs" node.
            .addOnSuccessListener { // Adds a success listener.
                userBlogReference.updateChildren(updatedData) // Updates the blog post in the user's "Blogs" node.
                    .addOnSuccessListener { // Adds a success listener.
                        binding.progressBar.visibility = View.GONE // Hides the progress bar.
                        binding.saveBlogButton.isEnabled = true // Enables the save button.
                        showToast("Blog updated successfully", FancyToast.SUCCESS) // Shows a success toast.
                        finish() // Finishes the activity.
                    }
                    .addOnFailureListener { // Adds a failure listener.
                        binding.progressBar.visibility = View.GONE // Hides the progress bar.
                        binding.saveBlogButton.isEnabled = true // Enables the save button.
                        showToast("Failed to update user's blog list", FancyToast.ERROR) // Shows an error toast.
                    }
            }
            .addOnFailureListener { // Adds a failure listener.
                binding.progressBar.visibility = View.GONE // Hides the progress bar.
                binding.saveBlogButton.isEnabled = true // Enables the save button.
                showToast("Failed to update blog", FancyToast.ERROR) // Shows an error toast.
            }
    }

    private fun isNetworkAvailable(): Boolean { // Defines the method to check for network availability.
        val connectivityManager = // Gets an instance of the ConnectivityManager.
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return connectivityManager?.activeNetwork?.let { // Checks if there is an active network.
            val capabilities = connectivityManager.getNetworkCapabilities(it) // Gets the network capabilities.
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || // Checks if the network has Wi-Fi transport.
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true || // Checks if the network has cellular transport.
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true // Checks if the network has Ethernet transport.
        } ?: false // Returns false if there is no active network.
    }

    private fun showToast(message: String, type: Int) { // Defines the method to show a custom toast.
        FancyToast.makeText( // Creates a FancyToast.
            this, // The context.
            message, // The message to display.
            FancyToast.LENGTH_SHORT, // The duration of the toast.
            type, // The type of toast (e.g., success, error, info).
            R.mipmap.blog_app_icon_round, // The icon to display.
            false // Whether to show the default Android icon.
        ).show() // Shows the toast.
    }
}
