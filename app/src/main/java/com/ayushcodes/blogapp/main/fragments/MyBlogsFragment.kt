package com.ayushcodes.blogapp.main.fragments // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Context // Imports Context class for accessing application environment
import android.content.Intent // Imports Intent class for launching activities
import android.net.ConnectivityManager // Imports ConnectivityManager for handling network connections
import android.net.NetworkCapabilities // Imports NetworkCapabilities for checking network capabilities
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.view.LayoutInflater // Imports LayoutInflater to inflate XML layouts
import android.view.View // Imports View class for UI elements
import android.view.ViewGroup // Imports ViewGroup as the base class for layouts
import androidx.fragment.app.Fragment // Imports Fragment class
import androidx.lifecycle.lifecycleScope // Imports lifecycleScope for managing coroutines
import androidx.recyclerview.widget.LinearLayoutManager // Imports LinearLayoutManager for arranging RecyclerView items
import cn.pedant.SweetAlert.SweetAlertDialog // Imports SweetAlertDialog for nice alerts
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.adapter.BlogAdapter // Imports the custom adapter for the blog list
import com.ayushcodes.blogapp.databinding.FragmentMyBlogsBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.EditBlogActivity // EDITED: Imports EditBlogActivity for handling blog edits.
import com.ayushcodes.blogapp.main.ReadMore // Imports ReadMore activity
import com.ayushcodes.blogapp.model.BlogItemModel // Imports the data model for blog items
import com.ayushcodes.blogapp.repository.BlogRepository // Imports the repository for data operations
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data from Firebase
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling database errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for listening to data changes
import com.shashank.sony.fancytoastlib.FancyToast // Imports FancyToast for displaying custom toast messages
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Fragment responsible for displaying the list of blogs created by the currently logged-in user.
class MyBlogsFragment : Fragment() { // Defines MyBlogsFragment class inheriting from Fragment

    // Lazily initialize view binding for the fragment layout
    private lateinit var binding: FragmentMyBlogsBinding // Declares a private lateinit variable for the FragmentMyBlogsBinding.

    // Firebase Authentication and Database instances
    private lateinit var auth: FirebaseAuth // Declares a private lateinit variable for FirebaseAuth.
    private lateinit var database: FirebaseDatabase // Declares a private lateinit variable for FirebaseDatabase.

    // Adapter for the RecyclerView
    private lateinit var blogAdapter: BlogAdapter // Declares a private lateinit variable for the BlogAdapter.

    // Called to have the fragment instantiate its user interface view
    override fun onCreateView( // Overrides the onCreateView method of the Fragment class.
        inflater: LayoutInflater, // An inflater to inflate the fragment's layout.
        container: ViewGroup?, // The parent view that the fragment's UI should be attached to.
        savedInstanceState: Bundle? // A bundle to restore the fragment's state.
    ): View { // Specifies that this method returns a View.
        // Inflate the layout for this fragment
        binding = FragmentMyBlogsBinding.inflate(inflater, container, false) // Inflates the layout for this fragment using view binding.
        return binding.root // Returns the root view of the inflated layout.
    }

    // Called immediately after onCreateView has returned, but before any saved state has been restored in to the view
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { // Overrides the onViewCreated method of the Fragment class.
        super.onViewCreated(view, savedInstanceState) // Calls the superclass's implementation of onViewCreated.

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Initializes the FirebaseAuth instance.
        database = FirebaseDatabase.getInstance() // Initializes the FirebaseDatabase instance.

        // Configure the RecyclerView with the adapter
        blogAdapter = BlogAdapter( // Initializes the BlogAdapter.
            onReadMoreClick = { blogItem -> // Sets a lambda function to handle "Read More" clicks.
                val intent = Intent(requireContext(), ReadMore::class.java) // Creates an Intent to start the ReadMore activity.
                intent.putExtra("blogItem", blogItem) // Adds the clicked blog item as an extra to the intent.
                startActivity(intent) // Starts the ReadMore activity.
            },
            onLikeClick = { blogItem -> // Sets a lambda function to handle "Like" clicks.
                if (auth.currentUser == null) { // Checks if a user is currently logged in.
                    showToast("Please login first", FancyToast.INFO) // Shows an informational toast asking the user to log in.
                } else { // Executes if a user is logged in.
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Calls the toggleLike method in the BlogRepository.
                }
            },
            onSaveClick = { blogItem -> // Sets a lambda function to handle "Save" clicks.
                if (auth.currentUser == null) { // Checks if a user is currently logged in.
                    showToast("Please login first", FancyToast.INFO) // Shows an informational toast asking the user to log in.
                } else { // Executes if a user is logged in.
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Calls the toggleSave method in the BlogRepository.
                }
            },
            onEditClick = { blogItem -> // EDITED: Adds a click listener for the edit button.
                val intent = Intent(requireContext(), EditBlogActivity::class.java) // EDITED: Creates an intent to start the EditBlogActivity.
                intent.putExtra("blogItem", blogItem) // EDITED: Passes the selected blog item to the EditBlogActivity.
                startActivity(intent) // EDITED: Starts the EditBlogActivity.
            },
            onDeleteClick = { blogItem -> // Sets a lambda function to handle "Delete" clicks.
                showDeleteConfirmation(blogItem) // Calls the showDeleteConfirmation method to confirm the deletion.
            }
        )

        binding.myBlogsRecyclerView.apply { // Applies a block of code to the myBlogsRecyclerView.
            layoutManager = LinearLayoutManager(context) // Sets the layout manager for the RecyclerView to a LinearLayoutManager.
            adapter = blogAdapter // Sets the adapter for the RecyclerView.
        }

        observeInteractionState() // Calls the method to start observing UI interaction state.

        // Fetch the list of blogs created by the current user from Firebase
        val userId = auth.currentUser?.uid // Gets the unique ID of the current user.
        if (userId != null) { // Checks if the userId is not null.
            val userBlogsReference = database.reference.child("users").child(userId).child("Blogs") // Gets a reference to the user's blogs in the Firebase database.
            // Attach a listener to get updates when the user's blogs change
            userBlogsReference.addValueEventListener(object : ValueEventListener { // Adds a listener for data changes at the specified database reference.
                override fun onDataChange(snapshot: DataSnapshot) { // This method is called whenever the data at the listened reference changes.
                    val blogItems = mutableListOf<BlogItemModel>() // Creates a mutable list to store the blog items.
                    // Iterate through the snapshot and add each blog item to the list
                    for (blogSnapshot in snapshot.children) { // Loops through each child snapshot in the main snapshot.
                        val blogItem = blogSnapshot.getValue(BlogItemModel::class.java) // Converts the snapshot data into a BlogItemModel object.
                        if (blogItem != null) { // Checks if the conversion was successful.
                            blogItems.add(blogItem) // Adds the blog item to the list.
                        }
                    }
                    blogItems.reverse() // Reverses the list to show the newest blogs first.
                    
                    BlogRepository.initializeState(blogItems) // Initializes the state of the BlogRepository with the fetched blog items.
                    blogAdapter.submitList(blogItems) // Submits the list of blog items to the adapter to be displayed.
                }

                override fun onCancelled(error: DatabaseError) { // This method is called if the data listener is cancelled.
                    // Handle potential database errors here
                }
            })
        }
    }

    // Observes the global interaction state for UI updates
    private fun observeInteractionState() { // Defines a private method to observe interaction state.
        viewLifecycleOwner.lifecycleScope.launch { // Launches a coroutine that is scoped to the fragment's view lifecycle.
            BlogRepository.interactionState.collect { state -> // Collects data from the interactionState flow in the BlogRepository.
                blogAdapter.updateInteractionState(state) // Updates the adapter with the new interaction state.
            }
        }
    }

    // Shows a confirmation dialog before deleting a blog post
    private fun showDeleteConfirmation(blogItem: BlogItemModel) { // Defines a private method to show a delete confirmation dialog.
        val context = requireContext() // Gets the non-null context of the fragment.
        SweetAlertDialog(context, SweetAlertDialog.WARNING_TYPE) // Creates a SweetAlertDialog with a warning style.
            .setTitleText("Delete Blog") // Sets the title of the dialog.
            .setContentText("Do you surely want to delete this \"${blogItem.heading}\" article?") // Sets the descriptive content text of the dialog.
            .setConfirmText("Delete") // Sets the text for the confirmation button.
            .setConfirmClickListener { sDialog -> // Sets a listener for when the confirmation button is clicked.
                if (isNetworkAvailable(context)) { // Checks if there is an active network connection.
                    deleteBlogPost(blogItem, sDialog) // Calls the method to delete the blog post if network is available.
                } else { // Executes if there is no network connection.
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows an informational toast about the lack of network connection.
                    sDialog.dismissWithAnimation() // Dismisses the dialog with an animation.
                }
            }
            .setCancelText("Back") // Sets the text for the cancel button.
            .setCancelClickListener { it.dismissWithAnimation() } // Sets a listener to dismiss the dialog when the cancel button is clicked.
            .show() // Displays the configured dialog.
    }

    // Deletes the blog post from Firebase Database
    private fun deleteBlogPost(blogItem: BlogItemModel, dialog: SweetAlertDialog) { // Defines a private method to delete a blog post.
        requireContext() // Gets the non-null context of the fragment.
        val blogId = blogItem.blogId!! // Gets the non-null blogId from the blog item.
        val updates = mutableMapOf<String, Any?>() // Creates a mutable map to hold the database update operations.
        updates["/blogs/$blogId"] = null // Adds an operation to delete the blog from the main "blogs" node.
        updates["/users/${auth.currentUser!!.uid}/Blogs/$blogId"] = null // Adds an operation to delete the blog from the current user's "Blogs" node.

        val usersWhoLikedOrSavedRef = database.reference.child("users") // Gets a reference to the "users" node in the database.
        usersWhoLikedOrSavedRef.addListenerForSingleValueEvent(object : ValueEventListener { // Adds a listener that reads the data once.
            override fun onDataChange(snapshot: DataSnapshot) { // This method is called with the data snapshot when the read is complete.
                for (userSnapshot in snapshot.children) { // Loops through each user in the snapshot.
                    if (userSnapshot.child("likedBlogs").hasChild(blogId)) { // Checks if the user has the deleted blog in their "likedBlogs".
                        updates["/users/${userSnapshot.key}/likedBlogs/$blogId"] = null // Adds an operation to remove the blog from the user's "likedBlogs".
                    }
                    if (userSnapshot.child("savedBlogs").hasChild(blogId)) { // Checks if the user has the deleted blog in their "savedBlogs".
                        updates["/users/${userSnapshot.key}/savedBlogs/$blogId"] = null // Adds an operation to remove the blog from the user's "savedBlogs".
                    }
                }

                database.reference.updateChildren(updates).addOnCompleteListener { task -> // Executes all the delete operations as a single atomic update.
                    if (task.isSuccessful) { // Checks if the update operation was successful.
                        showToast("Blog Deleted Successfully", FancyToast.SUCCESS) // Shows a success toast.
                        dialog.dismissWithAnimation() // Dismisses the confirmation dialog.
                        // List update will happen automatically via ValueEventListener on userBlogsReference
                    } else { // Executes if the update operation failed.
                        showToast("Something went wrong", FancyToast.ERROR) // Shows an error toast.
                        dialog.dismissWithAnimation() // Dismisses the confirmation dialog.
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { // This method is called if the listener is cancelled.
                showToast("Something went wrong", FancyToast.ERROR) // Shows an error toast.
                dialog.dismissWithAnimation() // Dismisses the confirmation dialog.
            }
        })
    }

    // Checks network connectivity status
    private fun isNetworkAvailable(context: Context): Boolean { // Defines a private method to check for network availability.
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets the ConnectivityManager system service.
        val network = connectivityManager.activeNetwork ?: return false // Gets the currently active network, or returns false if there is none.
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets the capabilities of the active network, or returns false if there are none.
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Returns true if the network has Wi-Fi transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Returns true if the network has cellular transport.
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Returns true if the network has Ethernet transport.
    }

    // Shows a custom toast message to the user
    private fun showToast(message: String, type: Int) { // Defines a private method to show a custom toast.
        FancyToast.makeText(requireContext(), message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Creates and shows a FancyToast with the given parameters.
    }
}
