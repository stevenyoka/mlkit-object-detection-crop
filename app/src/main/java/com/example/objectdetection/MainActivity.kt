package com.example.objectdetection

import android.graphics.Bitmap
import android.os.Bundle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions


class MainActivity : ImageHelperActivity() {

    private var objectDetector: ObjectDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Multiple object detection in static images
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()

        objectDetector = ObjectDetection.getClient(options)

    }

    override fun runDetection(bitmap: Bitmap?) {
        if(bitmap != null) {
            val image = InputImage.fromBitmap(bitmap, 0)
            objectDetector!!.process(image)
                .addOnSuccessListener { detectedObjects: List<DetectedObject> ->
                    // Task completed successfully
                    val sb = StringBuilder()
                    val list: MutableList<BoxWithText> = ArrayList()
                    for (`object` in detectedObjects) {
                        for (label in `object`.labels) {
                            sb.append(label.text).append(" : ")
                                .append(label.confidence).append("\n")
                        }
                        if (!`object`.labels.isEmpty()) {
                            list.add(
                                BoxWithText(
                                    `object`.labels[0].text,
                                    `object`.boundingBox
                                )
                            )
                        } else {
                            list.add(BoxWithText("Unknown", `object`.boundingBox))
                        }
                    }
                    inputImageView!!.setImageBitmap(drawDetectionResult(bitmap, list))
                    if (detectedObjects.isEmpty()) {
                        outputTextView!!.text = "Could not detect!!"
                    } else {
                        outputTextView!!.text = sb.toString()
                    }
                }
                .addOnFailureListener { e: Exception ->
                    // Task failed with an exception
                    // ...
                    e.printStackTrace()
                }
        }
    }
}