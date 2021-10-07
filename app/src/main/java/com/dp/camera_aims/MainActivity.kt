package com.dp.camera_aims

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dp.camera_aims.service.ImageSaverImplService
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var selectImage: ImageView
    lateinit var originalImage: File
    lateinit var resizedImage: File
    val CAPTURE_IMAGE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectImage = findViewById<ImageView>(R.id.take_photo_view)
        selectImage.setOnClickListener(View.OnClickListener { takePicture() })

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


                if (rotatedBitmap != null) {
                    if (storageDirectory != null) {
                        ImageSaverImplService(this)
                            .setDirectoryName(storageDirectory.absolutePath)
                            ?.setFileName(resizedImage.name)
                            ?.save(rotatedBitmap)
                    }
                }

                Log.i("test", "Notworking")


                Log.i("test", "Working")
                selectImage.setImageBitmap(rotatedBitmap)
                originalImage!!.delete()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Photo not captured", Toast.LENGTH_SHORT).show()
            originalImage?.delete()
        }
    }

}


//    var fotoapparat: Fotoapparat? = null
//    val filename = "test.png"
//    val sd = Environment.DIRECTORY_PICTURES
//    val dest = File(sd, filename)
//    var fotoapparatState: FotoapparatState? = null
//    var cameraStatus: CameraState? = null
//    var flashState: FlashState? = null
//
//    val permissions = arrayOf(
//        android.Manifest.permission.CAMERA,
//        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
//        android.Manifest.permission.READ_EXTERNAL_STORAGE
//    )
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        var fab_camera = findViewById<FloatingActionButton>(R.id.fab_camera)
//        var fab_switch_camera = findViewById<FloatingActionButton>(R.id.fab_switch_camera)
//        var fab_flash = findViewById<FloatingActionButton>(R.id.fab_flash)
//        createFotoapparat()
//
//        cameraStatus = CameraState.BACK
//        flashState = FlashState.OFF
//        fotoapparatState = FotoapparatState.OFF
//
//        fab_camera.setOnClickListener {
//            takePhoto()
//        }
//
//        fab_switch_camera.setOnClickListener {
//            switchCamera()
//        }
//
//        fab_flash.setOnClickListener {
//            changeFlashState()
//        }
//    }
//
//    private fun createFotoapparat() {
//        val cameraView = findViewById<CameraView>(R.id.camera_view)
//
//        fotoapparat = Fotoapparat(
//            context = this,
//            view = cameraView,
//            scaleType = ScaleType.CenterCrop,
//            lensPosition = back(),
//            logger = loggers(
//                logcat()
//            ),
//            cameraErrorCallback = { error ->
//                println("Recorder errors: $error")
//            }
//        )
//    }
//
//    private fun changeFlashState() {
//        fotoapparat?.updateConfiguration(
//            CameraConfiguration(
//                flashMode = if (flashState == FlashState.TORCH) off() else torch()
//            )
//        )
//
//        if (flashState == FlashState.TORCH) flashState = FlashState.OFF
//        else flashState = FlashState.TORCH
//    }
//
//    private fun switchCamera() {
//        fotoapparat?.switchTo(
//            lensPosition = if (cameraStatus == CameraState.BACK) front() else back(),
//            cameraConfiguration = CameraConfiguration()
//        )
//
//        if (cameraStatus == CameraState.BACK) cameraStatus = CameraState.FRONT
//        else cameraStatus = CameraState.BACK
//    }
//
//    private fun takePhoto() {
//        if (hasNoPermissions()) {
//            requestPermission()
//        } else {
//            fotoapparat
//                ?.takePicture()
//                ?.saveToFile(dest)
//        }
//    }
//
//    override fun onStart() {
//        super.onStart()
//        if (hasNoPermissions()) {
//            requestPermission()
//        } else {
//            fotoapparat?.start()
//            fotoapparatState = FotoapparatState.ON
//        }
//    }
//
//    private fun hasNoPermissions(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
//        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.CAMERA
//        ) != PackageManager.PERMISSION_GRANTED
//    }
//
//    fun requestPermission() {
//        ActivityCompat.requestPermissions(this, permissions, 0)
//    }
//
//    override fun onStop() {
//        super.onStop()
//        fotoapparat?.stop()
//        FotoapparatState.OFF
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (!hasNoPermissions() && fotoapparatState == FotoapparatState.OFF) {
//            val intent = Intent(baseContext, MainActivity::class.java)
//            startActivity(intent)
//            finish()
//        }
//    }

/*}

enum class CameraState {
    FRONT, BACK
}

enum class FlashState {
    TORCH, OFF
}

enum class FotoapparatState {
    ON, OFF
}*/



