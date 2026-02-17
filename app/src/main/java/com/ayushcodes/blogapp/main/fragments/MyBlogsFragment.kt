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
    private lateinit var binding: FragmentMyBlogsBinding // Declares binding variable

    // Firebase Authentication and Database instances
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Adapter for the RecyclerView
    private lateinit var blogAdapter: BlogAdapter // Declares BlogAdapter instance

    // Called to have the fragment instantiate its user interface view
    override fun onCreateView( // Overrides onCreateView method
        inflater: LayoutInflater, // Inflater to inflate layout
        container: ViewGroup?, // Parent view group
        savedInstanceState: Bundle?
    ): View { // Returns the View
        // Inflate the layout for this fragment
        binding = FragmentMyBlogsBinding.inflate(inflater, container, false) // Inflates layout using binding
        return binding.root // Returns root view
    }

    // Called immediately after onCreateView has returned, but before any saved state has been restored in to the view
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { // Overrides onViewCreated method
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Configure the RecyclerView with the adapter
        blogAdapter = BlogAdapter( // Initializes BlogAdapter
            onReadMoreClick = { blogItem -> // Callback for read more click
                val intent = Intent(requireContext(), ReadMore::class.java) // Creates intent for ReadMore activity
                intent.putExtra("blogItem", blogItem) // Puts blog item extra
                startActivity(intent) // Starts ReadMore activity
            },
            onLikeClick = { blogItem -> // Callback for like click
                if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles like
                }
            },
            onSaveClick = { blogItem -> // Callback for save click
                if (auth.currentUser == null) { // Checks if user is not logged in
                    showToast("Please login first", FancyToast.INFO) // Shows login prompt
                } else { // Executed if logged in
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles save
                }
            },
            onEditClick = { blogItem -> // EDITED: Adds a click listener for the edit button.
                val intent = Intent(requireContext(), EditBlogActivity::class.java) // EDITED: Creates an intent to start the EditBlogActivity.
                intent.putExtra("blogItem", blogItem) // EDITED: Passes the selected blog item to the EditBlogActivity.
                startActivity(intent) // EDITED: Starts the EditBlogActivity.
            },
            onDeleteClick = { blogItem -> // Callback for delete click
                showDeleteConfirmation(blogItem) // Shows delete confirmation dialog
            }
        )

        binding.myBlogsRecyclerView.apply { // Applies configuration to RecyclerView
            layoutManager = LinearLayoutManager(context) // Sets LayoutManager
            adapter = blogAdapter // Sets adapter
        }

        observeInteractionState() // Calls observeInteractionState

        // Fetch the list of blogs created by the current user from Firebase
        val userId = auth.currentUser?.uid // Gets current user ID
        if (userId != null) { // Checks if user ID is not null
            val userBlogsReference = database.reference.child("users").child(userId).child("Blogs") // References user's blogs node
            // Attach a listener to get updates when the user's blogs change
            userBlogsReference.addValueEventListener(object : ValueEventListener { // Adds ValueEventListener
                override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                    val blogItems = mutableListOf<BlogItemModel>() // Creates mutable list for items
                    // Iterate through the snapshot and add each blog item to the list
                    for (blogSnapshot in snapshot.children) { // Iterates through each snapshot child
                        val blogItem = blogSnapshot.getValue(BlogItemModel::class.java) // deserializes blog item
                        if (blogItem != null) { // Checks if blog item is not null
                            blogItems.add(blogItem) // Adds to list
                        }
                    }
                    blogItems.reverse() // Newest first // Reverses list
                    
                    BlogRepository.initializeState(blogItems) // Initializes repository state
                    blogAdapter.submitList(blogItems) // Submits list to adapter
                }

                override fun onCancelled(error: DatabaseError) { // Called on cancellation
                    // Handle potential database errors here
                }
            })
        }
    }

    // Observes the global interaction state for UI updates
    private fun observeInteractionState() { // Defines observeInteractionState method
        viewLifecycleOwner.lifecycleScope.launch { // Launches coroutine in view lifecycle scope
            BlogRepository.interactionState.collect { state -> // Collects interaction state
                blogAdapter.updateInteractionState(state) // Updates adapter
            }
        }
    }

    // Shows a confirmation dialog before deleting a blog post
    private fun showDeleteConfirmation(blogItem: BlogItemModel) { // Defines showDeleteConfirmation method
        val context = requireContext() // Gets context
        SweetAlertDialog(context, SweetAlertDialog.WARNING_TYPE) // Creates SweetAlertDialog
            .setTitleText("Delete Blog") // Sets title
            .setContentText("Do you surely want to delete this \"${blogItem.heading}\" article?") // Sets content text
            .setConfirmText("Delete") // Sets confirm button text
            .setConfirmClickListener { sDialog -> // Sets confirm listener
                if (isNetworkAvailable(context)) { // Checks if network is available
                    deleteBlogPost(blogItem, sDialog) // Calls deleteBlogPost
                } else { // Executed if network is unavailable
                    showToast("Please check your internet connection..", FancyToast.INFO) // Shows toast
                    sDialog.dismissWithAnimation() // Dismisses dialog
                }
            }
            .setCancelText("Back") // Sets cancel text
            .setCancelClickListener { it.dismissWithAnimation() } // Sets cancel listener
            .show() // Shows dialog
    }

    // Deletes the blog post from Firebase Database
    private fun deleteBlogPost(blogItem: BlogItemModel, dialog: SweetAlertDialog) { // Defines deleteBlogPost method
        val context = requireContext() // Gets context
        val blogId = blogItem.blogId!! // Gets blog ID
        val updates = mutableMapOf<String, Any?>() // Creates mutable map for updates
        updates["/blogs/$blogId"] = null // Adds global blog path to updates for deletion
        updates["/users/${auth.currentUser!!.uid}/Blogs/$blogId"] = null // Adds user blog path to updates for deletion

        val usersWhoLikedOrSavedRef = database.reference.child("users") // References users node
        usersWhoLikedOrSavedRef.addListenerForSingleValueEvent(object : ValueEventListener { // Adds single value listener
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data received
                for (userSnapshot in snapshot.children) { // Iterates through all users
                    if (userSnapshot.child("likedBlogs").hasChild(blogId)) { // Checks if user liked the blog
                        updates["/users/${userSnapshot.key}/likedBlogs/$blogId"] = null // Adds liked path to updates for deletion
                    }
                    if (userSnapshot.child("savedBlogs").hasChild(blogId)) { // Checks if user saved the blog
                        updates["/users/${userSnapshot.key}/savedBlogs/$blogId"] = null // Adds saved path to updates for deletion
                    }
                }

                database.reference.updateChildren(updates).addOnCompleteListener { task -> // Performs atomic update
                    if (task.isSuccessful) { // Checks if successful
                        showToast("Blog Deleted Successfully", FancyToast.SUCCESS) // Shows success toast
                        dialog.dismissWithAnimation() // Dismisses dialog
                        // List update will happen automatically via ValueEventListener on userBlogsReference
                    } else { // Executed if failed
                        showToast("Something went wrong", FancyToast.ERROR) // Shows error toast
                        dialog.dismissWithAnimation() // Dismisses dialog
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { // Called on cancellation
                showToast("Something went wrong", FancyToast.ERROR) // Shows error toast
                dialog.dismissWithAnimation() // Dismisses dialog
            }
        })
    }

    // Checks network connectivity status
    private fun isNetworkAvailable(context: Context): Boolean { // Defines isNetworkAvailable method
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager // Gets ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false // Gets active network
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false // Gets network capabilities
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || // Checks WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || // Checks Cellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) // Checks Ethernet
    }

    // Shows a custom toast message to the user
    private fun showToast(message: String, type: Int) { // Defines showToast method
        FancyToast.makeText(requireContext(), message, FancyToast.LENGTH_SHORT, type, R.mipmap.blog_app_icon_round, false).show() // Shows FancyToast
    }
}
