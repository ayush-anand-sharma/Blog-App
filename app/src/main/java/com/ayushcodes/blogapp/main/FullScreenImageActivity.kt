package com.ayushcodes.blogapp.main // Defines the package for this file.

import android.os.Bundle // Imports the Bundle class for passing data.
import androidx.appcompat.app.AppCompatActivity // Imports the base class for activities.
import com.ayushcodes.blogapp.databinding.ActivityFullScreenImageBinding // Imports the binding class for the layout.
import com.bumptech.glide.Glide // Imports Glide for image loading.

class FullScreenImageActivity : AppCompatActivity() { // Defines the activity for displaying a full-screen image.

    private lateinit var binding: ActivityFullScreenImageBinding // Declares the binding variable for the layout.

    override fun onCreate(savedInstanceState: Bundle?) { // Called when the activity is first created.
        super.onCreate(savedInstanceState) // Calls the parent class's onCreate method.
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater) // Inflates the layout for the activity.
        setContentView(binding.root) // Sets the content view to the root of the binding.

        val imageUrl = intent.getStringExtra("image_url") // Retrieves the image URL from the intent.
        if (imageUrl != null) { // Checks if the image URL is not null.
            Glide.with(this) // Begins a Glide request.
                .load(imageUrl) // Loads the image from the URL.
                .into(binding.fullScreenImageView) // Displays the image in the specified ImageView.
        }

        binding.backButton.setOnClickListener { // Sets a click listener for the back button.
            finish() // Finishes the activity and returns to the previous screen.
        }
    }
}
