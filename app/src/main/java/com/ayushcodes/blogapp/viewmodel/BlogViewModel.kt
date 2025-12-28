package com.ayushcodes.blogapp.viewmodel // Defines the package name for this file

// Import necessary Android and library classes
import androidx.lifecycle.ViewModel // Imports ViewModel class
import androidx.lifecycle.viewModelScope // Imports viewModelScope for coroutines
import com.ayushcodes.blogapp.model.BlogItemModel // Imports BlogItemModel class
import com.ayushcodes.blogapp.repository.BlogRepository // Imports BlogRepository class
import kotlinx.coroutines.flow.SharingStarted // Imports SharingStarted for flows
import kotlinx.coroutines.flow.stateIn // Imports stateIn extension for flows

// Handles global like/save state synchronization across all screens
class BlogViewModel : ViewModel() { // Defines BlogViewModel class inheriting from ViewModel

    // Exposes the interaction state as a StateFlow, scoped to the ViewModel
    val interactionState = BlogRepository.interactionState.stateIn( // Converts repository flow to StateFlow
        scope = viewModelScope, // Scopes to ViewModel lifecycle
        started = SharingStarted.WhileSubscribed(5000), // Keeps flow active for 5 seconds after last subscriber
        initialValue = emptyMap() // Sets initial value
    )

    // Initializes the repository state with a list of blogs
    fun initializeState(blogs: List<BlogItemModel>) { // Method to initialize state
        BlogRepository.initializeState(blogs) // Calls repository initialize method
    }

    // Toggles the like status of a blog item
    fun toggleLike(blogItem: BlogItemModel) { // Method to toggle like
        val blogId = blogItem.blogId ?: return // Gets blog ID, returns if null
        BlogRepository.toggleLike(blogId, blogItem) // Calls repository toggleLike method
    }

    // Toggles the save status of a blog item
    fun toggleSave(blogItem: BlogItemModel) { // Method to toggle save
        val blogId = blogItem.blogId ?: return // Gets blog ID, returns if null
        BlogRepository.toggleSave(blogId, blogItem) // Calls repository toggleSave method
    }
}
