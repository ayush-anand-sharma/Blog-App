package com.ayushcodes.blogapp.adapter // Defines the package name for this file

// Import necessary Android and library classes
import android.view.LayoutInflater // Imports LayoutInflater to inflate XML layouts
import android.view.View // Imports View class for UI elements
import android.view.ViewGroup // Imports ViewGroup as the base class for layouts
import androidx.recyclerview.widget.DiffUtil // Imports DiffUtil for calculating list differences
import androidx.recyclerview.widget.ListAdapter // Imports ListAdapter for handling lists in RecyclerView
import androidx.recyclerview.widget.RecyclerView // Imports RecyclerView for displaying lists
import com.ayushcodes.blogapp.R // Imports the R class for accessing resources
import com.ayushcodes.blogapp.databinding.BlogItemBinding // Imports the generated binding class for the blog item layout
import com.ayushcodes.blogapp.model.BlogItemModel // Imports the data model for blog items
import com.ayushcodes.blogapp.model.PostInteractionState // Imports the model for interaction state (likes, saves)
import com.bumptech.glide.Glide // Imports Glide for image loading
import com.bumptech.glide.request.RequestOptions // Imports RequestOptions for Glide configuration
import com.google.firebase.auth.FirebaseAuth // Imports FirebaseAuth for user authentication

// Handles global like/save state synchronization across all screens
class BlogAdapter( // Defines the BlogAdapter class inheriting from ListAdapter
    private val onReadMoreClick: (BlogItemModel) -> Unit, // Lambda callback for "Read More" click
    private val onLikeClick: (BlogItemModel) -> Unit, // Lambda callback for "Like" button click
    private val onSaveClick: (BlogItemModel) -> Unit, // Lambda callback for "Save" button click
    private val onDeleteClick: ((BlogItemModel) -> Unit)? = null // Optional lambda callback for "Delete" button click
) : ListAdapter<BlogItemModel, BlogAdapter.BlogViewHolder>(BlogDiffCallback()) { // Inherits ListAdapter with BlogViewHolder and DiffCallback

    private var interactionState: Map<String, PostInteractionState> = emptyMap() // Map to store interaction states, initialized as empty
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid // Gets the current user's ID from Firebase

    // Updates the interaction state and notifies the adapter of changes
    fun updateInteractionState(newState: Map<String, PostInteractionState>) { // Method to update interaction state map
        interactionState = newState // Updates the local interaction state map
        notifyItemRangeChanged(0, itemCount, PAYLOAD_INTERACTION_STATE) // Notifies adapter to rebind views with payload
    }

    // Creates a new ViewHolder instance
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogViewHolder { // Overrides method to create ViewHolders
        val binding = BlogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false) // Inflates the blog item layout
        return BlogViewHolder(binding) // Returns a new BlogViewHolder instance
    }

    // Binds data to the ViewHolder at the specified position
    override fun onBindViewHolder(holder: BlogViewHolder, position: Int) { // Overrides method to bind data to views
        holder.bind(getItem(position)) // Calls bind on the holder with the item at position
    }

    // Partially binds data to the ViewHolder if payloads are present
    override fun onBindViewHolder(holder: BlogViewHolder, position: Int, payloads: MutableList<Any>) { // Overrides method for partial updates
        if (payloads.contains(PAYLOAD_INTERACTION_STATE)) { // Checks if the payload contains interaction state update
            holder.updateInteraction(getItem(position)) // Updates only the interaction part of the view
        } else { // Executed if no specific payload matches
            super.onBindViewHolder(holder, position, payloads) // Calls super to perform full bind
        }
    }

    // ViewHolder class to hold references to the views for each list item
    inner class BlogViewHolder(private val binding: BlogItemBinding) : RecyclerView.ViewHolder(binding.root) { // Defines inner ViewHolder class

        // Binds the blog item data to the UI elements
        fun bind(blogItem: BlogItemModel) { // Method to bind data to the view
            binding.apply { // Scope function to access binding properties
                heading.text = blogItem.heading // Sets the heading text
                userName.text = blogItem.fullName // Sets the user name text
                date.text = blogItem.date // Sets the date text
                blogPost.text = blogItem.blogPost // Sets the blog post text
                
                Glide.with(profile.context) // Initiates Glide with the profile view's context
                    .load(blogItem.profileImage) // Loads the profile image URL
                    .apply(RequestOptions.circleCropTransform()) // Applies a circle crop transformation
                    .placeholder(R.drawable.default_avatar) // Sets a placeholder image
                    .error(R.drawable.default_avatar) // Sets an error image
                    .into(profile) // Loads the image into the profile ImageView

                readMoreButton.setOnClickListener { onReadMoreClick(blogItem) } // Sets click listener for "Read More"
                
                // Profile click listeners (kept from original)
                val context = root.context // Gets context from the root view
                val profileClickListener = View.OnClickListener { // Creates a common click listener for profile
                     val intent = if (blogItem.userId == currentUserId) { // Checks if the blog author is the current user
                         android.content.Intent(context, com.ayushcodes.blogapp.main.ProfilePage::class.java) // Intent for own profile
                     } else { // Executed if author is another user
                         android.content.Intent(context, com.ayushcodes.blogapp.main.UserProfileActivity::class.java).apply { // Intent for other user's profile
                             putExtra("userId", blogItem.userId) // Puts user ID extra
                         }
                     }
                     context.startActivity(intent) // Starts the profile activity
                }
                profile.setOnClickListener(profileClickListener) // Sets listener on profile image
                userName.setOnClickListener(profileClickListener) // Sets listener on user name

                if (onDeleteClick != null && blogItem.userId == currentUserId) { // Checks if delete action is available and user owns the post
                    deleteButton.visibility = View.VISIBLE // Makes delete button visible
                    deleteButton.setOnClickListener { onDeleteClick.invoke(blogItem) } // Sets click listener for delete button
                } else { // Executed if delete is not allowed
                    deleteButton.visibility = View.GONE // Hides delete button
                }

                likeButton.setOnClickListener { onLikeClick(blogItem) } // Sets click listener for like button
                postSaveButton.setOnClickListener { onSaveClick(blogItem) } // Sets click listener for save button

                updateInteraction(blogItem) // Updates the interaction UI (likes/saves)
            }
        }

        // Updates only the interaction-related UI elements (likes, saves)
        fun updateInteraction(blogItem: BlogItemModel) { // Method to update interaction UI
            val blogId = blogItem.blogId ?: return // Gets blog ID, returns if null
            val state = interactionState[blogId] // Gets interaction state for this blog ID

            val isLiked = state?.isLiked ?: false // Default to false if not loaded yet, or check blogItem.likes if needed but state should be source of truth // Gets liked status
            val isSaved = state?.isSaved ?: false // Gets saved status
            val count = state?.likeCount ?: blogItem.likeCount // Gets like count, defaulting to item's count

            binding.likeButton.setImageResource(if (isLiked) R.drawable.red_like_heart_icon else R.drawable.white_and_black_like_heart_icon) // Sets like button icon
            binding.postSaveButton.setImageResource(if (isSaved) R.drawable.bookmark_semi_red_icon else R.drawable.bookmark_red_icon) // Sets save button icon
            binding.likeCount.text = count.toString() // Sets the like count text
        }
    }

    // DiffCallback for efficient list updates
    class BlogDiffCallback : DiffUtil.ItemCallback<BlogItemModel>() { // Defines DiffCallback class
        override fun areItemsTheSame(oldItem: BlogItemModel, newItem: BlogItemModel): Boolean { // Checks if items represent the same object
            return oldItem.blogId == newItem.blogId // Returns true if blog IDs match
        }

        override fun areContentsTheSame(oldItem: BlogItemModel, newItem: BlogItemModel): Boolean { // Checks if item contents are identical
            return oldItem == newItem // Returns true if objects are equal
        }
    }

    // Companion object for constants
    companion object { // Defines companion object
        private const val PAYLOAD_INTERACTION_STATE = "payload_interaction_state" // Constant for payload key
    }
}
