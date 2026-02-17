package com.ayushcodes.blogapp.repository // Defines the package name for this file

// Import necessary Android and library classes
import com.ayushcodes.blogapp.model.BlogItemModel // Imports BlogItemModel class
import com.ayushcodes.blogapp.model.PostInteractionState // Imports PostInteractionState class
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication
import com.google.firebase.database.DataSnapshot // Imports DataSnapshot for reading data
import com.google.firebase.database.DatabaseError // Imports DatabaseError for handling errors
import com.google.firebase.database.FirebaseDatabase // Imports FirebaseDatabase for database access
import com.google.firebase.database.MutableData // Imports MutableData for transactions
import com.google.firebase.database.Transaction // Imports Transaction class
import com.google.firebase.database.ValueEventListener // Imports ValueEventListener for listening to data
import kotlinx.coroutines.CoroutineScope // Imports CoroutineScope
import kotlinx.coroutines.Dispatchers // Imports Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow // Imports MutableStateFlow
import kotlinx.coroutines.flow.StateFlow // Imports StateFlow
import kotlinx.coroutines.flow.asStateFlow // Imports asStateFlow
import kotlinx.coroutines.flow.update // Imports update extension for StateFlow

// Handles global like/save state synchronization across all screens
object BlogRepository { // Defines BlogRepository singleton object

    private val database = FirebaseDatabase.getInstance() // Gets FirebaseDatabase instance
    private val auth = FirebaseAuth.getInstance() // Gets FirebaseAuth instance
    private var currentUserId: String? = null // CHANGE: Keep track of the current user ID to correctly detach listeners.


    private val _interactionState = MutableStateFlow<Map<String, PostInteractionState>>(emptyMap()) // Private mutable interaction state
    val interactionState: StateFlow<Map<String, PostInteractionState>> = _interactionState.asStateFlow() // Public immutable interaction state

    private val likedBlogsListener = object : ValueEventListener { // Listener for user's liked blogs
        override fun onDataChange(snapshot: DataSnapshot) { // Called when liked blogs data changes
            val likedBlogIds = snapshot.children.mapNotNull { it.key }.toSet() // Gets set of liked blog IDs
            updateLocalLikeState(likedBlogIds) // Updates local state
        }
        override fun onCancelled(error: DatabaseError) {} // Called on cancellation
    }

    private val savedBlogsListener = object : ValueEventListener { // Listener for user's saved blogs
        override fun onDataChange(snapshot: DataSnapshot) { // Called when saved blogs data changes
            val savedBlogIds = snapshot.children.mapNotNull { it.key }.toSet() // Gets set of saved blog IDs
            updateLocalSaveState(savedBlogIds) // Updates local state
        }
        override fun onCancelled(error: DatabaseError) {} // Called on cancellation
    }

    // Keep track of which blog IDs we have active listeners on for like counts
    private val activeBlogListeners = mutableMapOf<String, ValueEventListener>() // Map to store active listeners

    init { // Initializer block
        // CHANGE: Rewrote the auth state listener for more robust handling of login and logout.
        auth.addAuthStateListener { firebaseAuth -> // Adds auth state listener
            val newUser = firebaseAuth.currentUser
            if (newUser != null) {
                // User is logged in.
                currentUserId = newUser.uid
                // Attach listeners for the new user.
                attachUserListeners(currentUserId!!)
            } else {
                // User is logged out.
                if (currentUserId != null) {
                    // Detach listeners from the old user to prevent memory leaks.
                    detachUserListeners(currentUserId!!)
                    currentUserId = null
                }
                // Clear only the user-specific parts of the state (likes/saves), not the whole thing.
                clearUserInteractionState()
            }
        }
    }
    // CHANGE: New function to attach listeners for a specific user.
    private fun attachUserListeners(userId: String) {
        database.getReference("users").child(userId).child("likedBlogs")
            .addValueEventListener(likedBlogsListener)
        database.getReference("users").child(userId).child("savedBlogs")
            .addValueEventListener(savedBlogsListener)
    }

    // CHANGE: New function to detach listeners, preventing memory leaks on logout.
    private fun detachUserListeners(userId: String) {
        database.getReference("users").child(userId).child("likedBlogs")
            .removeEventListener(likedBlogsListener)
        database.getReference("users").child(userId).child("savedBlogs")
            .removeEventListener(savedBlogsListener)
    }

    // CHANGE: New function to clear only user-specific state on logout.
    private fun clearUserInteractionState() {
        // This function is the core of the fix.
        _interactionState.update { currentState ->
            val newState = currentState.toMutableMap()
            // We iterate through every blog post we're tracking...
            newState.forEach { (blogId, state) ->
                // ...and we create a new state that resets `isLiked` and `isSaved` to false,
                // but—crucially—preserves the public `likeCount`.
                newState[blogId] = state.copy(isLiked = false, isSaved = false)
            }
            // The result is that the like count no longer resets to zero on logout.
            newState
        }
    }


    // Call this when data is loaded from API to initialize state
    fun initializeState(blogs: List<BlogItemModel>) { // Method to initialize state with blog list
        val userId = auth.currentUser?.uid // Gets current user ID
        _interactionState.update { currentState -> // Updates interaction state
            val newState = currentState.toMutableMap() // Creates mutable copy of current state
            blogs.forEach { blog -> // Iterates through each blog
                val blogId = blog.blogId ?: return@forEach // Gets blog ID, skips if null
                val existingState = currentState[blogId] // Gets existing state for blog

                // Preserve existing user interaction state if available (source of truth is the user listeners),
                // otherwise fallback to blog data
                val isLiked = existingState?.isLiked ?: (userId != null && blog.likes?.containsKey(userId) == true) // Determines isLiked status
                val isSaved = existingState?.isSaved ?: false  // Determines isSaved status
                val likeCount = existingState?.likeCount ?: blog.likeCount // EDITED: Use existing like count if available to prevent overwriting with stale data.

                newState[blogId] = PostInteractionState( // Creates new PostInteractionState
                    blogId = blogId, // Sets blog ID
                    isLiked = isLiked, // Sets isLiked
                    isSaved = isSaved, // Sets isSaved
                    likeCount = likeCount // Sets likeCount
                )
                
                // Ensure we listen to like count updates for this blog if not already
                if (!activeBlogListeners.containsKey(blogId)) { // Checks if listener exists
                   monitorBlogLikeCount(blogId) // Adds monitor
                }
            }
            newState // Returns new state
        }
    }
    
    private fun monitorBlogLikeCount(blogId: String) { // Method to monitor like count for a blog
        val listener = object : ValueEventListener { // Creates ValueEventListener
            override fun onDataChange(snapshot: DataSnapshot) { // Called when data changes
                val likeCount = snapshot.getValue(Int::class.java) ?: 0 // Gets like count
                _interactionState.update { currentMap -> // Updates interaction state
                    val currentState = currentMap[blogId] ?: PostInteractionState(blogId) // Gets current state for blog
                    
                    // If we are currently toggling, don't let the listener overwrite our optimistic count immediately
                    // This prevents the "flash" of old data if the listener is slightly faster than the transaction commit
                    if (currentState.isLikeToggling) { // Checks if toggling
                        currentMap // Returns current map unchanged
                    } else if (currentState.likeCount != likeCount) { // Checks if like count changed
                        currentMap + (blogId to currentState.copy(likeCount = likeCount)) // Updates like count
                    } else { // Executed if no change
                        currentMap // Returns current map
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {} // Called on cancellation
        }
        database.getReference("blogs").child(blogId).child("likeCount").addValueEventListener(listener) // Adds listener to likeCount node
        activeBlogListeners[blogId] = listener // Stores listener in map
    }

    private fun updateLocalLikeState(likedIds: Set<String>) { // Method to update local liked state
        _interactionState.update { currentMap -> // Updates interaction state
            val newMap = currentMap.toMutableMap() // Creates mutable copy
            // Update existing entries
            newMap.keys.forEach { blogId -> // Iterates through existing keys
                val state = newMap[blogId]!! // Gets state
                if (state.isLiked != likedIds.contains(blogId)) { // Checks if liked status changed
                    // Update state. If we were toggling, we can consider the toggle complete now that we have confirmation from the user's list
                    newMap[blogId] = state.copy(isLiked = likedIds.contains(blogId), isLikeToggling = false) // Updates state
                }
            }
            // Add missing entries if any
            likedIds.forEach { blogId -> // Iterates through liked IDs
                if (!newMap.containsKey(blogId)) { // Checks if ID is missing from map
                    newMap[blogId] = PostInteractionState(blogId, isLiked = true) // Adds new state
                }
            }
            newMap // Returns new map
        }
    }

    private fun updateLocalSaveState(savedIds: Set<String>) { // Method to update local saved state
        _interactionState.update { currentMap -> // Updates interaction state
            val newMap = currentMap.toMutableMap() // Creates mutable copy
            newMap.keys.forEach { blogId -> // Iterates through existing keys
                val state = newMap[blogId]!! // Gets state
                if (state.isSaved != savedIds.contains(blogId)) { // Checks if saved status changed
                    newMap[blogId] = state.copy(isSaved = savedIds.contains(blogId)) // Updates state
                }
            }
            savedIds.forEach { blogId -> // Iterates through saved IDs
                if (!newMap.containsKey(blogId)) { // Checks if ID is missing from map
                    newMap[blogId] = PostInteractionState(blogId, isSaved = true) // Adds new state
                }
            }
            newMap // Returns new map
        }
    }

    fun toggleLike(blogId: String, blogItem: BlogItemModel) { // Method to toggle like
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        val currentState = _interactionState.value[blogId] ?: PostInteractionState(blogId, likeCount = blogItem.likeCount) // Gets current state
        val isCurrentlyLiked = currentState.isLiked // Gets current liked status

        // Optimistic Update: Mark as toggling to ignore incoming stale updates from public count
        val newLikeCount = if (isCurrentlyLiked) (currentState.likeCount - 1).coerceAtLeast(0) else currentState.likeCount + 1 // Calculates new like count
        _interactionState.update { // Updates interaction state
            it + (blogId to currentState.copy(isLiked = !isCurrentlyLiked, likeCount = newLikeCount, isLikeToggling = true)) // Sets optimistic state
        }

        val blogRef = database.getReference("blogs").child(blogId) // References blog node
        val userLikedRef = database.getReference("users").child(userId).child("likedBlogs").child(blogId) // References user liked node

        blogRef.runTransaction(object : Transaction.Handler { // Runs transaction on blog node
            override fun doTransaction(currentData: MutableData): Transaction.Result { // Transaction logic
                val blog = currentData.getValue(BlogItemModel::class.java) // deserializes blog
                    ?: return Transaction.success(currentData) // Returns success if null

                if (blog.likes == null) blog.likes = mutableMapOf() // Initializes likes map if null

                if (isCurrentlyLiked) { // Checks if currently liked
                    if (blog.likes!!.containsKey(userId)) { // Checks if user liked
                        blog.likes!!.remove(userId) // Removes like
                        blog.likeCount = (blog.likeCount - 1).coerceAtLeast(0) // Decrements count
                    }
                } else { // Executed if not liked
                    if (!blog.likes!!.containsKey(userId)) { // Checks if user hasn't liked
                        blog.likes!![userId] = true // Adds like
                        blog.likeCount += 1 // Increments count
                    }
                }
                currentData.value = blog // Sets new data
                return Transaction.success(currentData) // Returns success
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) { // Called on completion
                if (error != null) { // Checks for error
                    // Revert on failure
                    _interactionState.update { // Updates interaction state
                        it + (blogId to currentState.copy(isLikeToggling = false)) // Revert to original state // Reverts state
                    }
                } else { // Executed on success
                    // Update user's liked list. This will trigger likedBlogsListener which will clear isLikeToggling
                    if (isCurrentlyLiked) { // Checks if was liked
                        userLikedRef.removeValue() // Removes from user liked list
                    } else { // Executed if was not liked
                        userLikedRef.setValue(blogItem) // Adds to user liked list
                    }
                    
                    // Fallback: If for some reason listener doesn't fire, ensure we clear the toggle flag after a short delay or implicitly via next update
                    // But relying on listener is cleaner.
                }
            }
        })
    }

    fun toggleSave(blogId: String, blogItem: BlogItemModel) { // Method to toggle save
        val userId = auth.currentUser?.uid ?: return // Gets user ID, returns if null
        val currentState = _interactionState.value[blogId] ?: PostInteractionState(blogId) // CHANGE: Fixed typo here from _interactionAcoState to _interactionState
        val isCurrentlySaved = currentState.isSaved // Gets current saved status

        // Optimistic Update
        _interactionState.update { // Updates interaction state
            it + (blogId to currentState.copy(isSaved = !isCurrentlySaved)) // Sets optimistic state
        }

        val userSavedRef = database.getReference("users").child(userId).child("savedBlogs").child(blogId) // References user saved node

        if (isCurrentlySaved) { // Checks if currently saved
            userSavedRef.removeValue().addOnFailureListener { // Removes value, adds failure listener
                // Revert
                _interactionState.update { it + (blogId to currentState) } // Reverts state on failure
            }
        } else { // Executed if not saved
            userSavedRef.setValue(blogItem).addOnFailureListener { // Sets value, adds failure listener
                // Revert
                _interactionState.update { it + (blogId to currentState) } // Reverts state on failure
            }
        }
    }
}
