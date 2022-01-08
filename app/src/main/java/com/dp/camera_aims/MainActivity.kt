package com.dp.camera_aims

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.os.Process.getUidForName
import android.os.Process.killProcess
import android.os.StrictMode
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
import com.github.ybq.android.spinkit.animation.AnimationUtils.stop
import leakcanary.AppWatcher
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var selectImage: ImageView
    private lateinit var btn: Button
    private lateinit var loadingScreen: SpinKitView
    private lateinit var originalImage: File
    private lateinit var resizedImage: File
    private val CAPTURE_IMAGE_REQUEST = 1

    private val cloudinary =
        Cloudinary("cloudinary://651998212777852:zmLnemqvVP3LI_2HDe1fPB4oG7M@johnsad")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppWatcher.objectWatcher.watch(this, "View was detached")
        selectImage = findViewById(R.id.take_photo_view) as ImageView
        btn = findViewById(R.id.btn) as Button
        loadingScreen = findViewById(R.id.progBar) as SpinKitView
        loadingScreen.visibility = GONE
        selectImage.setOnClickListener({ takePicture() })

    }

    fun takePicture() {
        if (ContextCompat.checkSelfPermission(
                this,
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
                            this,
                            "Authorized activity to open a camera",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (originalImage != null) {
                        val photoURI = FileProvider.getUriForFile(
                            this,
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

                val originalImageBitmap: Bitmap? = ImageSaverImplService(this)
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
            Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Photo not captured", Toast.LENGTH_SHORT).show()
            originalImage?.delete()
        }
    }

    private fun clickUpload(originalImage: File) {

        btn.setOnClickListener {
            /*TRANSFORMATION WITH UPLOAD*/

            loadingScreen.visibility = VISIBLE
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
                    /*RAW UPLOAD*/
                    /* cloudinary.uploader().upload(File("$originalImage"),
                         ObjectUtils.asMap("sample", "tete")) *//*sample folder*/
                    var eager = response
                    Log.i("PUTANGNA MO", "${eager}")

                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
                loadingScreen.post(Runnable {
                    loadingScreen.visibility = GONE
                })
            }.start()

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

}