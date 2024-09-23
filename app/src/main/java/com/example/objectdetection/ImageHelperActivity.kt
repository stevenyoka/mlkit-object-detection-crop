package com.example.objectdetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Region
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min


abstract class ImageHelperActivity : AppCompatActivity() {
    val LOG_TAG = "MLImageHelper"
    var photoFile: File? = null
    protected var inputImageView: ImageView? = null
        private set
    protected var outputTextView: TextView? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_helper)
        inputImageView = findViewById(R.id.imageView)
        outputTextView = findViewById(R.id.textView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_READ_EXTERNAL_STORAGE
                )
            }
        }

        val viewFlipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        val inAnimation = AnimationUtils.loadAnimation(this,android.R.anim.slide_in_left)
        val outAnimation = AnimationUtils.loadAnimation(this,android.R.anim.slide_out_right)

        viewFlipper.inAnimation = inAnimation
        viewFlipper.outAnimation = outAnimation


        val previousBtn = findViewById<Button>(R.id.previousBtn)
        previousBtn.setOnClickListener {
            viewFlipper.showPrevious()
        }
        val nextBtn = findViewById<Button>(R.id.nextBtn)
        nextBtn.setOnClickListener {
            viewFlipper.showNext()
        }
    }

    fun onTakeImage(view: View?) {
        // create Intent to take a picture and return control to the calling application
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Create a File reference for future access
        photoFile = getPhotoFileUri(SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()) + ".jpg")

        // wrap File object into a content provider
        // required for API >= 24
        // See https://guides.codepath.com/android/Sharing-Content-with-Intents#sharing-files-with-api-24-or-higher
        val fileProvider = FileProvider.getUriForFile(
            this, "com.yoka.fileprovider1",
            photoFile!!
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)

        // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
        // So as long as the result is not null, it's safe to use the intent.
        if (intent.resolveActivity(packageManager) != null) {
            // Start the image capture intent to take photo
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
        }
    }

    // Returns the File for a photo stored on disk given the fileName
    fun getPhotoFileUri(fileName: String): File {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        val mediaStorageDir =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), LOG_TAG)

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d(LOG_TAG, "failed to create directory")
        }

        // Return the file target for the photo based on filename
        return File(mediaStorageDir.path + File.separator + fileName)
    }

    private val capturedImage: Bitmap
        /**
         * getCapturedImage():
         * Decodes and crops the captured image from camera.
         */
        private get() {
            // Get the dimensions of the View
            val targetW = inputImageView!!.width
            val targetH = inputImageView!!.height
            var bmOptions = BitmapFactory.Options()
            bmOptions.inJustDecodeBounds = true
            BitmapFactory.decodeFile(photoFile!!.absolutePath)
            val photoW = bmOptions.outWidth
            val photoH = bmOptions.outHeight
            val scaleFactor =
                max(1.0, min((photoW / targetW).toDouble(), (photoH / targetH).toDouble()))
                    .toInt()
            bmOptions = BitmapFactory.Options()
            bmOptions.inJustDecodeBounds = false
            bmOptions.inSampleSize = scaleFactor
            bmOptions.inMutable = true
            return BitmapFactory.decodeFile(photoFile!!.absolutePath, bmOptions)
        }

    private fun rotateIfRequired(bitmap: Bitmap) {
        try {
            val exifInterface = ExifInterface(photoFile!!.absolutePath)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotateImage(bitmap, 90f)
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                rotateImage(bitmap, 180f)
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                rotateImage(bitmap, 270f)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Rotate the given bitmap.
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.getWidth(), source.getHeight(),
            matrix, true
        )
    }

    fun onPickImage(view: View?) {
        // create Intent to take a picture and return control to the calling application
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("image/*")

        // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
        // So as long as the result is not null, it's safe to use the intent.
        if (intent.resolveActivity(packageManager) != null) {
            // Start the image capture intent to take photo
            startActivityForResult(intent, PICK_IMAGE_ACTIVITY_REQUEST_CODE)
        }
    }

    protected abstract fun runDetection(bitmap: Bitmap?)
    protected fun loadFromUri(photoUri: Uri?): Bitmap? {
        var image: Bitmap? = null
        try {
            image = if (Build.VERSION.SDK_INT > 27) {
                val source = ImageDecoder.createSource(
                    this.contentResolver,
                    photoUri!!
                )
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(this.contentResolver, photoUri)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return image
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val bitmap = capturedImage
                rotateIfRequired(bitmap)
                inputImageView!!.setImageBitmap(bitmap)
                runDetection(bitmap)
            } else { // Result was a failure
                Toast.makeText(this, "Picture wasn't taken!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == PICK_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val takenImage = loadFromUri(data!!.data)
                inputImageView!!.setImageBitmap(takenImage)
                runDetection(takenImage)
            } else { // Result was a failure
                Toast.makeText(this, "Picture wasn't selected!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Draw bounding boxes around objects together with the object's name.
     */
    protected fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: List<BoxWithText>
    ): Bitmap {
//        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//        val canvas = Canvas(outputBitmap)
//        val pen = Paint()
//        pen.textAlign = Paint.Align.LEFT

        val viewFlipper = findViewById<ViewFlipper>(R.id.viewFlipper)

        for (box in detectionResults) {
            val src = bitmap.copy(Bitmap.Config.ARGB_8888, true)
//            val output = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888)
//            val canvas = Canvas(output)
//            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
//
//            paint.setColor(-0x1000000)
//            canvas.drawRect(box.rect, paint)
//            paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
//            canvas.drawBitmap(src, 0f, 0f, paint)

            val output = cropBitmapByRect(src, box.rect)

            val imageView = ImageView(this)
            imageView.setImageBitmap(output)
            imageView.layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT,
            )
            viewFlipper.addView(imageView)
        }

//        for (box in detectionResults) {
//            // draw bounding box
//            pen.setColor(Color.RED)
//            pen.strokeWidth = 8f
//            pen.style = Paint.Style.STROKE
//            canvas.drawRect(box.rect, pen)
//            val tagSize = Rect(0, 0, 0, 0)
//
//            // calculate the right font size
//            pen.style = Paint.Style.FILL_AND_STROKE
//            pen.setColor(Color.YELLOW)
//            pen.strokeWidth = 2f
//            pen.textSize = 96f
//            pen.getTextBounds(box.text, 0, box.text.length, tagSize)
//            val fontSize: Float = pen.textSize * box.rect.width() / tagSize.width()
//
//            // adjust the font size so texts are inside the bounding box
//            if (fontSize < pen.textSize) {
//                pen.textSize = fontSize
//            }
//            var margin: Float = (box.rect.width() - tagSize.width()) / 2.0f
//            if (margin < 0f) margin = 0f
//            canvas.drawText(
//                box.text, box.rect.left + margin,
//                box.rect.top + tagSize.height().toFloat(), pen
//            )
//        }
//        return outputBitmap
        return bitmap
    }

    fun cropBitmapByRect(originalBitmap: Bitmap, rect: Rect): Bitmap {
        // Ensure the Rect does not exceed the bounds of the Bitmap
        val width = originalBitmap.width
        val height = originalBitmap.height
        val safeRect = Rect(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            rect.right.coerceAtMost(width),
            rect.bottom.coerceAtMost(height)
        )

        // Crop the Bitmap using the safeRect
        return Bitmap.createBitmap(
            originalBitmap,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )
    }

    companion object {
        const val CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1034
        const val PICK_IMAGE_ACTIVITY_REQUEST_CODE = 1064
        const val REQUEST_READ_EXTERNAL_STORAGE = 2031
    }
}