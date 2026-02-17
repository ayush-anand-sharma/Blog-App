package com.ayushcodes.blogapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.ayushcodes.blogapp.R
import com.yalantis.ucrop.UCrop
import java.io.File

class CropperActivity : AppCompatActivity() {
    private lateinit var uCropView: com.yalantis.ucrop.view.UCropView // uCrop view for displaying the image cropping UI
    private lateinit var imgRight: ImageView // ImageView for the confirm button
    private lateinit var imgWrong: ImageView // ImageView for the cancel button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cropper)

        uCropView = findViewById(R.id.uCropView) // Initialize the uCropView
        imgRight = findViewById(R.id.img_right) // Initialize the confirm button
        imgWrong = findViewById(R.id.img_wrong) // Initialize the cancel button

        val sourceUri = intent.data // Get the image URI from the intent
        val destinationUri = Uri.fromFile(File(cacheDir, "IMG_" + System.currentTimeMillis())) // Create a destination URI for the cropped image

        val options = UCrop.Options() // Create a new UCrop.Options object to customize the cropper
        options.setCircleDimmedLayer(true) // Set the dimmed layer to be a circle
        options.setCompressionQuality(90) // Set the compression quality of the cropped image
        options.setShowCropFrame(true) // Show the crop frame
        options.setShowCropGrid(true) // Show the crop grid

        if (sourceUri != null) { // If the source URI is not null
            UCrop.of(sourceUri, destinationUri) // Start the uCrop activity
                .withOptions(options) // Set the custom options
                .withAspectRatio(1f, 1f) // Set the aspect ratio to 1:1
                .withMaxResultSize(1000, 1000) // Set the max result size
                .start(this, UCrop.REQUEST_CROP) // Start the cropping activity
        }

        imgRight.setOnClickListener { // Set an OnClickListener for the confirm button
            val resultUri = UCrop.getOutput(intent) // Get the cropped image URI
            if (resultUri != null) { // If the result URI is not null
                val intent = Intent() // Create a new Intent
                intent.data = resultUri // Set the cropped image URI as the data of the intent
                setResult(RESULT_OK, intent) // Set the result to RESULT_OK and pass the intent back
                finish() // Finish the activity
            } else { // If the result URI is null
                setResult(RESULT_CANCELED) // Set the result to RESULT_CANCELED
                finish() // Finish the activity
            }
        }

        imgWrong.setOnClickListener { // Set an OnClickListener for the cancel button
            setResult(RESULT_CANCELED) // Set the result to RESULT_CANCELED
            finish() // Finish the activity
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { // Handle the result from the uCrop activity
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) { // If the result is OK and the request code is for cropping
            val resultUri = UCrop.getOutput(data!!) // Get the cropped image URI
            val intent = Intent() // Create a new Intent
            intent.data = resultUri // Set the cropped image URI as the data of the intent
            setResult(RESULT_OK, intent) // Set the result to RESULT_OK and pass the intent back
            finish() // Finish the activity
        } else if (resultCode == UCrop.RESULT_ERROR) { // If there was an error during cropping
            val cropError = UCrop.getError(data!!) // Get the cropping error
            // Handle the cropping error
            setResult(RESULT_CANCELED) // Set the result to RESULT_CANCELED
            finish() // Finish the activity
        }
    }
}
