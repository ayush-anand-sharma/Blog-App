package com.ayushcodes.blogapp.main.fragments // Defines the package name for this file

// Import necessary Android and library classes
import android.content.Intent // Imports Intent class for launching activities
import android.os.Bundle // Imports Bundle for passing data between Android components
import android.view.LayoutInflater // Imports LayoutInflater to inflate XML layouts
import android.view.View // Imports View class for UI elements
import android.view.ViewGroup // Imports ViewGroup as the base class for layouts
import androidx.fragment.app.Fragment // Imports Fragment class
import androidx.lifecycle.lifecycleScope // Imports lifecycleScope for managing coroutines
import androidx.recyclerview.widget.LinearLayoutManager // Imports LinearLayoutManager for arranging RecyclerView items
import com.ayushcodes.blogapp.adapter.BlogAdapter // Imports the custom adapter for the blog list
import com.ayushcodes.blogapp.databinding.FragmentLikedBlogsBinding // Imports the generated binding class for the layout
import com.ayushcodes.blogapp.main.ReadMore // Imports ReadMore activity
import com.ayushcodes.blogapp.model.BlogItemModel // Imports the data model for blog items
import com.ayushcodes.blogapp.repository.BlogRepository // Imports the repository for data operations
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data from Firebase
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling database errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for accessing the Realtime Database
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for listening to data changes
import kotlinx.coroutines.launch // Imports launch for starting coroutines

// Fragment responsible for displaying the list of blogs liked by the currently logged-in user.
class LikedBlogsFragment : Fragment() { // Defines LikedBlogsFragment class inheriting from Fragment

    // Lazily initialize view binding for the fragment layout
    private lateinit var binding: FragmentLikedBlogsBinding // Declares binding variable

    // Firebase Authentication instance to manage user authentication
    private lateinit var auth: FirebaseAuth // Declares FirebaseAuth instance

    // Firebase Database instance to interact with Realtime Database
    private lateinit var database: FirebaseDatabase // Declares FirebaseDatabase instance

    // Adapter for the RecyclerView to display blog items
    private lateinit var blogAdapter: BlogAdapter // Declares BlogAdapter instance

    // Called to have the fragment instantiate its user interface view
    override fun onCreateView( // Overrides onCreateView method
        inflater: LayoutInflater, // Inflater to inflate layout
        container: ViewGroup?, // Parent view group
        savedInstanceState: Bundle? // Saved state bundle
    ): View { // Returns the View
        // Inflate the layout for this fragment
        binding = FragmentLikedBlogsBinding.inflate(inflater, container, false) // Inflates layout using binding
        return binding.root // Returns root view
    }

    // Called immediately after onCreateView has returned, but before any saved state has been restored in to the view
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { // Overrides onViewCreated method
        super.onViewCreated(view, savedInstanceState) // Calls superclass onViewCreated

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
        database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance

        // Configure the RecyclerView with the adapter
        blogAdapter = BlogAdapter( // Initializes BlogAdapter
            onReadMoreClick = { blogItem -> // Callback for read more click
                // Handle click event to read more about the blog
                val intent = Intent(requireContext(), ReadMore::class.java) // Creates intent for ReadMore activity
                intent.putExtra("blogItem", blogItem) // Puts blog item extra
                startActivity(intent) // Starts ReadMore activity
            },
            onLikeClick = { blogItem -> // Callback for like click
                if (auth.currentUser == null) { // Checks if user is not logged in
                    // Toast handling or prompt logic could be added here
                } else { // Executed if logged in
                    BlogRepository.toggleLike(blogItem.blogId ?: "", blogItem) // Toggles like
                }
            },
            onSaveClick = { blogItem -> // Callback for save click
                if (auth.currentUser == null) { // Checks if user is not logged in
                    // Toast handling or prompt logic could be added here
                } else { // Executed if logged in
                    BlogRepository.toggleSave(blogItem.blogId ?: "", blogItem) // Toggles save
                }
            }
        )

        binding.likedBlogsRecyclerView.apply { // Applies configuration to RecyclerView
            layoutManager = LinearLayoutManager(context) // Sets LayoutManager
            adapter = blogAdapter // Sets adapter
        }

        observeInteractionState() // Calls observeInteractionState

        // Fetch the list of liked blogs for the current user from Firebase
        val userId = auth.currentUser?.uid // Gets current user ID
        if (userId != null) { // Checks if user ID is not null
            binding.progressBar.visibility = View.VISIBLE // Show the progress bar while loading.
            val likedBlogsReference = database.reference.child("users").child(userId).child("likedBlogs") // References likedBlogs node
            // Attach a listener to get updates when the liked blogs change
            likedBlogsReference.addValueEventListener(object : ValueEventListener { // Adds a listener for a single value event
                override fun onDataChange(snapshot: DataSnapshot) { // Called when the data is retrieved.
                    val likedBlogIds = snapshot.children.mapNotNull { it.key } // Get the list of liked blog IDs.
                    if (likedBlogIds.isEmpty()) { // If there are no liked blogs.
                        binding.progressBar.visibility = View.GONE // Hide the progress bar.
                        blogAdapter.submitList(emptyList()) // Submit an empty list to the adapter.
                        return // Return from the function.
                    }

                    val blogsRef = database.reference.child("blogs") // Get a reference to the main blogs node.
                    val likedBlogItems = mutableListOf<BlogItemModel>() // Create a mutable list to hold the liked blog items.
                    var remaining = likedBlogIds.size // Keep track of the remaining blogs to fetch.

                    likedBlogIds.forEach { blogId -> // For each liked blog ID.
                        blogsRef.child(blogId).addListenerForSingleValueEvent(object : ValueEventListener { // Add a listener to get the blog data.
                            override fun onDataChange(blogSnapshot: DataSnapshot) { // Called when the blog data is retrieved.
                                val blogItem = blogSnapshot.getValue(BlogItemModel::class.java) // Get the blog item.
                                if (blogItem != null) { // If the blog item is not null.
                                    likedBlogItems.add(blogItem) // Add the blog item to the list.
                                }
                                remaining-- // Decrement the remaining count.
                                if (remaining == 0) { // If all blogs have been fetched.
                                    binding.progressBar.visibility = View.GONE // Hide the progress bar.
                                    likedBlogItems.reverse() // Reverse the list to show the most recently liked blogs first.
                                    BlogRepository.initializeState(likedBlogItems) // Initialize the repository state.
                                    blogAdapter.submitList(likedBlogItems) // Submit the list to the adapter.
                                }
                            }

                            override fun onCancelled(error: DatabaseError) { // Called if the data retrieval is cancelled.
                                remaining-- // Decrement the remaining count.
                                if (remaining == 0) { // If all blogs have been fetched.
                                    binding.progressBar.visibility = View.GONE // Hide the progress bar.
                                }
                            }
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) { // Called if the data retrieval is cancelled.
                    binding.progressBar.visibility = View.GONE // Hide the progress bar.
                }
            })
        }
    }

    // Observe global state changes for likes and saves to update UI
    private fun observeInteractionState() { // Defines observeInteractionState method
        viewLifecycleOwner.lifecycleScope.launch { // Launches coroutine in view lifecycle scope
            BlogRepository.interactionState.collect { state -> // Collects interaction state
                blogAdapter.updateInteractionState(state) // Updates adapter
            }
        }
    }
}
