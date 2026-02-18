package com.ayushcodes.blogapp.main // Defines the package for the class.

import androidx.lifecycle.LiveData // Imports the LiveData class, which is a data holder class that can be observed within a given lifecycle.
import androidx.lifecycle.MutableLiveData // Imports the MutableLiveData class, which is a LiveData that can be modified.
import androidx.lifecycle.ViewModel // Imports the ViewModel class, which is designed to store and manage UI-related data in a lifecycle-conscious way.

class SharedViewModel : ViewModel() { // Defines the SharedViewModel class, which inherits from ViewModel.

    private val _profileUpdated = MutableLiveData<Boolean>() // Creates a private MutableLiveData instance to hold a boolean value, indicating whether the profile has been updated.
    val profileUpdated: LiveData<Boolean> = _profileUpdated // Exposes the LiveData to other classes so they can observe it without being able to change it.

    fun notifyProfileUpdated() { // Defines a function to notify observers that the profile has been updated.
        _profileUpdated.value = true // Sets the value of the LiveData to true, triggering any active observers.
    }

    fun onProfileUpdateNotified() { // Defines a function to reset the LiveData value after the update has been handled.
        _profileUpdated.value = false // Resets the value to false, preventing the observers from being triggered again unnecessarily.
    }
}