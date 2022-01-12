package com.dp.camera_aims

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.cloudinary.Cloudinary
import com.cloudinary.Transformation
import com.cloudinary.utils.ObjectUtils
import com.dp.camera_aims.service.ImageSaverImplService
import com.github.ybq.android.spinkit.SpinKitView
import com.google.android.material.textfield.TextInputEditText
import leakcanary.AppWatcher
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.AccessController.getContext
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLException


class MainActivity : AppCompatActivity(), ComponentCallbacks2 {

    private lateinit var selectImage: ImageView
    private lateinit var link: TextInputEditText
    private lateinit var btn: Button
    private lateinit var loadingScreen: SpinKitView
    private lateinit var originalImage: File
    private lateinit var resizedImage: File
    private val CAPTURE_IMAGE_REQUEST = 1

    private val cloudinary =
        Cloudinary("cloudinary://651998212777852:zmLnemqvVP3LI_2HDe1fPB4oG7M@johnsad")

    /*TODO

    * https://medium.com/@banmarkovic/what-is-context-in-android-and-which-one-should-you-use-e1a8c6529652
    *
    *  FOR REFERENCES IF YOU WANT TO KNOW HOW CONTEXT WORK AND REMOVED MEMORY LEAKS
    *
    * */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppWatcher.objectWatcher.watch(getContext(), "View was detached")
        selectImage = findViewById(R.id.take_photo_view) as ImageView
        link = findViewById(R.id.link) as TextInputEditText
        btn = findViewById(R.id.btn) as Button
        loadingScreen = findViewById(R.id.progBar) as SpinKitView
        loadingScreen.visibility = GONE
        selectImage.setOnClickListener({ takePicture() })

    }

    fun takePicture() {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                0
            )
        } else {
            Thread {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(this.getPackageManager()) != null) {
                    try {
                        originalImage = createImageUniqueFile()
                    } catch (error: IOException) {
                        Toast.makeText(
                            getApplicationContext(),
                            "Authorized activity to open a camera",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (originalImage != null) {
                        val photoURI = FileProvider.getUriForFile(
                            getApplicationContext(),
                            "com.dp.camera_aims.fileprovider",
                            originalImage
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        startActivityForResult(
                            takePictureIntent,
                            CAPTURE_IMAGE_REQUEST
                        )
                    }
                }
            }.start()
        }
    }

    @Throws(IOException::class)
    fun createImageUniqueFile(): File {
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "IMG " + timeStamp + "_"
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDirectory /* directory */
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                takePicture()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Thread {
            selectImage.post {
                Log.i("PompomPogi", "Pogi ako")
                capture(requestCode, resultCode)
            }
        }.start()
    }


    fun capture(requestCode: Int, resultCode: Int) {
        if (requestCode == CAPTURE_IMAGE_REQUEST && resultCode == RESULT_OK) {
            try {
                resizedImage = createImageUniqueFile()
                val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

                val originalImageBitmap: Bitmap? = ImageSaverImplService(getBaseContext()  )
                    .setDirectoryName(storageDirectory?.absolutePath)
                    ?.setFileName(originalImage.name)
                    ?.load()

                clickUpload(this.originalImage)

                var imageBitMap = savedLocal(storageDirectory, originalImage, originalImageBitmap)

                Log.i("test", "Working")
                selectImage.setImageBitmap(imageBitMap)

               // Glide.with(this).load("http://goo.gl/gEgYUd").into(selectImage);
                Glide.with(this)
                    .load(imageBitMap)
                .into(selectImage);

            } catch (e: IOException) {
                e.printStackTrace()
            }
            Toast.makeText(getApplicationContext(), "Photo captured successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplicationContext(), "Photo not captured", Toast.LENGTH_SHORT).show()
            originalImage?.delete()
        }
    }

    private fun clickUpload(originalImage: File) {

        btn.setOnClickListener {
            /*TRANSFORMATION WITH UPLOAD*/

            if (checkForInternet(this)){
                Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                loadingScreen.visibility = VISIBLE

                try {
                    Thread {
                        try {
                            var response = cloudinary.uploader().upload(
                                "${originalImage}",
                                ObjectUtils.asMap(
                                    "transformation",
                                    Transformation<Transformation<*>>()
                                        .width(300)
                                        .height(300) /*Height the one who limit*/
                                        .crop("limit")
                                )
                            )

                            /*Url of permanent image*/
                            var eager = response.get("secure_url")

                            Log.i("PUTANGNA MO", "${eager}")

                        } catch (e: SSLException ) {
                            e.printStackTrace()
                        }
                        loadingScreen.post(Runnable {
                            loadingScreen.visibility = GONE
                            selectImage.setImageDrawable(null);

                        })
                    }.start()
                }catch (ex: FileNotFoundException){
                    ex.printStackTrace()
                }

            }
            else {
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            }

        }

    }

    private fun checkForInternet(context: Context): Boolean {

        // register activity with the connectivity manager service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // if the android version is equal to M
        // or greater we need to use the
        // NetworkCapabilities to check what type of
        // network has the internet connection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Returns a Network object corresponding to
            // the currently active default data network.
            val network = connectivityManager.activeNetwork ?: return false

            // Representation of the capabilities of an active network.
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // Indicates this network uses a Wi-Fi transport,
                // or WiFi has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                // Indicates this network uses a Cellular transport. or
                // Cellular has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                // else return false
                else -> false
            }
        } else {
            // if the android version is below M
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Glide.get(this).clearMemory()
    }

    fun savedLocal(
        storageDirectory: File?,
        originalImage: File,
        originalImageBitmap: Bitmap?
    ): Bitmap? {
        var resizedImageBitmap = originalImageBitmap
        val matrix = Matrix()
        val exif = ExifInterface(originalImage.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == 6) { // back cam portrait
            Log.i("activity", "orientation 6")
            resizedImageBitmap =
                Bitmap.createScaledBitmap(originalImageBitmap!!, 1080, 810, false)
            matrix.postRotate(90f)
        } else if (orientation == 8) { // front cam portrait
            Log.i("activity", "orientation 8")
            resizedImageBitmap =
                Bitmap.createScaledBitmap(originalImageBitmap!!, 1080, 810, false)
            matrix.postRotate(270f)
        } else if (orientation == 1) { // landscape
            Log.i("activity", "orientation 1")
            resizedImageBitmap =
                Bitmap.createScaledBitmap(originalImageBitmap!!, 810, 1080, false)
        } else if (orientation == 3) { //landscape
            Log.i("activity", "orientation 3")
            resizedImageBitmap =
                Bitmap.createScaledBitmap(originalImageBitmap!!, 810, 1080, false)
            matrix.postRotate(180f)
        }

        Log.i("activity", "default yan")
        val rotatedBitmap = resizedImageBitmap?.let {
            Bitmap.createBitmap(
                it,
                0,
                0,
                resizedImageBitmap.width,
                resizedImageBitmap.height,
                matrix,
                true
            )
        }

        /*todo saved in local*/
        if (rotatedBitmap != null) {
            if (storageDirectory != null) {
                ImageSaverImplService(this)
                    .setDirectoryName(storageDirectory.absolutePath)
                    ?.setFileName(resizedImage.name)
                    ?.save(rotatedBitmap)
            }
        }

        return rotatedBitmap
    }
    /*TODO NOT WORKING*/
//    override fun onTrimMemory(level: Int) {
//
//        // Determine which lifecycle or system event was raised.
//        when (level) {
//
//            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
//                /*
//                   Release any UI objects that currently hold memory.
//
//                   The user interface has moved to the background.
//                */
//            }
//
//            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
//            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
//            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
//                /*
//                   Release any memory that your app doesn't need to run.
//
//                   The device is running low on memory while the app is running.
//                   The event raised indicates the severity of the memory-related event.
//                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
//                   begin killing background processes.
//                */
//            }
//
//            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
//            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
//            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
//                /*
//                   Release as much memory as the process can.
//
//                   The app is on the LRU list and the system is running low on memory.
//                   The event raised indicates where the app sits within the LRU list.
//                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
//                   the first to be terminated.
//                */
//            }
//
//            else -> {
//                /*
//                  Release any non-critical data structures.
//
//                  The app received an unrecognized memory level value
//                  from the system. Treat this as a generic low-memory message.
//                */
//            }
//        }
//    }

}