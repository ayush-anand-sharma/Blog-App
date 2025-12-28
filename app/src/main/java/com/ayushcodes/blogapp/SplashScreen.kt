@file:Suppress("all")
package com.ayushcodes.blogapp // Defines the package name for this file

import android.annotation.SuppressLint // Imports SuppressLint annotation to suppress specific lint warnings
import android.content.Intent // Imports Intent class for launching activities
import android.os.Bundle // Imports Bundle for passing data between Android components
import androidx.activity.enableEdgeToEdge // Imports enableEdgeToEdge to support edge-to-edge display
import androidx.appcompat.app.AppCompatActivity // Imports AppCompatActivity as the base class for activities
import com.ayushcodes.blogapp.register.WelcomeScreen // Imports the WelcomeScreen activity class
import java.util.concurrent.Executors // Imports Executors for managing threads

// This activity serves as a splash screen, displayed when the app is launched.
@SuppressLint("CustomSplashScreen") // Suppresses lint warning about using a custom splash screen
class SplashScreen : AppCompatActivity() { // Defines the SplashScreen class inheriting from AppCompatActivity
    // Called when the activity is first created.
    override fun onCreate(savedInstanceState: Bundle?) { // Overrides the onCreate method to initialize the activity
        super.onCreate(savedInstanceState) // Calls the superclass implementation of onCreate
        enableEdgeToEdge() // Enables edge-to-edge display for the activity
        setContentView(R.layout.activity_splash_screen) // Sets the layout for this activity from resources

        // A handler to delay the transition to the WelcomeScreen.
        Executors.newSingleThreadExecutor().execute { // Creates a single thread executor and executes a task
            // Used for handling exceptions during the splash screen delay
            try { // Starts a try block to handle potential exceptions
                Thread.sleep(3000) // 3-second delay. // Pauses the current thread for 3000 milliseconds
                // Start the WelcomeScreen activity.
                startActivity(Intent(this, WelcomeScreen::class.java)) // Creates an intent and starts the WelcomeScreen activity
                // Finish the splash screen activity so it's not in the back stack.
                finish() // Finishes the SplashScreen activity so the user cannot go back to it
            // Catches InterruptedException if the thread is interrupted during sleep.
            } catch (e: InterruptedException) { // Catches an InterruptedException if the sleep is interrupted
                e.printStackTrace() // Prints the stack trace of the exception
            }
        }
    }
}
