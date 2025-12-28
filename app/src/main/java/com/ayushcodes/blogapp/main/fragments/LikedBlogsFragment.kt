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
            val likedBlogsReference = database.reference.child("users").child(userId).child("likedBlogs") // References likedBlogs node
            // Attach a listener to get updates when the liked blogs change
            likedBlogsReference.addValueEventListener(object : ValueEventListener { // Adds ValueEventListener
                override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                    val newLikedItems = mutableListOf<BlogItemModel>() // Creates mutable list for items
                    val pendingCount = snapshot.childrenCount // Gets count of children
                    var loadedCount = 0 // Initializes loaded count

                    if (pendingCount == 0L) { // Checks if no liked blogs
                         blogAdapter.submitList(emptyList()) // Submits empty list
                         return // Returns
                    }

                    // Iterate through the snapshot and fetch details for each liked blog
                    for (blogSnapshot in snapshot.children) { // Iterates through each liked blog snapshot
                        val likedBlogId = blogSnapshot.key // Gets blog ID key
                        if (likedBlogId != null) { // Checks if key is not null
                            // Retrieve the full blog item details from the "blogs" node using the blog ID
                            val blogReference = database.reference.child("blogs").child(likedBlogId) // References specific blog node
                            blogReference.addListenerForSingleValueEvent(object : ValueEventListener { // Adds single value listener
                                override fun onDataChange(snapshot: DataSnapshot) { // Called when blog data is received
                                    val blogItem = snapshot.getValue(BlogItemModel::class.java) // deserializes blog item
                                    if (blogItem != null) { // Checks if blog item is not null
                                        newLikedItems.add(blogItem) // Adds to list
                                    }
                                    loadedCount++ // Increments loaded count
                                    if (loadedCount.toLong() == pendingCount) { // Checks if all items loaded
                                        // Initialize repository state
                                        BlogRepository.initializeState(newLikedItems) // Initializes repository state
                                        blogAdapter.submitList(newLikedItems.toList()) // Submits list to adapter
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) { // Called on cancellation
                                    loadedCount++ // Increments loaded count to avoid stuck loading
                                }
                            })
                        } else { // Executed if key is null
                            loadedCount++ // Increments loaded count
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) { // Called on cancellation of main listener
                    // Handle potential database errors here
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
