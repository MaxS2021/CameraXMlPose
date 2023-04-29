package com.smastcomm.cameraxposdetect

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.smastcomm.cameraxposdetect.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    private lateinit var outputDir: File
    private lateinit var cameraExecutor: ExecutorService
    private var cameraFacing = CameraSelector.DEFAULT_BACK_CAMERA
    lateinit var cameraSelector: CameraSelector
    lateinit var camera: Camera
    private var imageCapture: ImageCapture? = null
    private lateinit var videoCapture: VideoCapture

    private lateinit var poseDetector: PoseDetector
    private lateinit var imageAnalysis: ImageAnalysis

    private var poseDetectState = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        getPermission()

        outputDir = getOutputDir()
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        binding.flipCamera.setOnClickListener {
            if (cameraFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraFacing = CameraSelector.DEFAULT_FRONT_CAMERA

            } else {
                cameraFacing = CameraSelector.DEFAULT_BACK_CAMERA
            }
            //Log.d(Constants.TAG, "$cameraFacing")
            startCamera()
        }

        binding.capture.setOnClickListener {
            takePhoto()
        }

        binding.startVideo.setOnClickListener {
            startVideo()
        }

        binding.stoptVideo.setOnClickListener {
            stopVideo()
        }

        binding.toggleFlash.setOnClickListener {
            flashToggle()
        }

        binding.startPose.setOnClickListener {
            if (!poseDetectState) {
                binding.startPose.setImageResource(R.drawable.ic_bofy_blue)


            } else {
                binding.parentLayout.removeAllViews()

                binding.parentLayout.addView(binding.viewFinder)
                binding.parentLayout.addView(binding.startPose)
                binding.parentLayout.addView(binding.flipCamera)
                binding.parentLayout.addView(binding.toggleFlash)
                binding.parentLayout.addView(binding.startVideo)
                binding.parentLayout.addView(binding.stoptVideo)
                binding.parentLayout.addView(binding.capture)

                binding.startPose.setImageResource(R.drawable.ic_body)
                cameraFacing = CameraSelector.DEFAULT_BACK_CAMERA
            }
            poseDetectState = !poseDetectState
            startCamera()
        }

    }

    @SuppressLint("RestrictedApi")
    private fun stopVideo() {
        binding.startVideo.setImageResource(R.drawable.ic_play_white)
        videoCapture.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    @SuppressLint("MissingPermission", "RestrictedApi")
    private fun startVideo() {
        binding.startVideo.setImageResource(R.drawable.ic_play_red)
        val videoFile = File(outputDir,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault())
                .format(System.currentTimeMillis()) + ".mp4")

        val outputFileOptions = VideoCapture.OutputFileOptions.Builder(videoFile).build()

        videoCapture.startRecording(outputFileOptions,ContextCompat.getMainExecutor(this),
            @SuppressLint("RestrictedApi")
            object: VideoCapture.OnVideoSavedCallback{
                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                    Log.d(Constants.TAG,"Видео записано")
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.d(Constants.TAG,"Ошибка записи Видео: " + message)
                }

            })
    }

    private fun takePhoto() {
        val imageCapture = imageCapture?: return
        val photoFile = File(outputDir,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault())
                .format(System.currentTimeMillis()) + ".png")

        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOption, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Фото сохранено  $savedUri"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(Constants.TAG, "Ошибка: ${exception.message}", exception )
                }
            })
    }

    private fun flashToggle() {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value === 0) {
                camera.cameraControl.enableTorch(true)
                binding.toggleFlash.setImageResource(R.drawable.baseline_flash_off_24)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.toggleFlash.setImageResource(R.drawable.baseline_flash_on_24)
            }
        } else {
            runOnUiThread {
                Toast.makeText(this@MainActivity,"Вспышка не доступна",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    @SuppressLint("NewApi", "RestrictedApi")
    private fun  startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvaider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()

            poseDetector = PoseDetection.getClient(options)

            if (poseDetectState) {
                cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
            } else {
                cameraSelector = cameraFacing //CameraSelector.DEFAULT_BACK_CAMERA
            }

            imageCapture = ImageCapture.Builder().build()

            val point = Point()
            val size = display?.getRealSize(point)

            videoCapture = VideoCapture.Builder()
                .setTargetResolution(Size(point.x,point.y))
                .setAudioBitRate(320000)
                .setAudioSampleRate(44100)
                .setAudioChannelCount(2)
                .build()

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(point.x,point.y))
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->

                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val mediaImage = imageProxy.image

                if (mediaImage != null) {

                    val processImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

                    poseDetector.process(processImage)
                        .addOnSuccessListener {pose->

                            val allPoseLandmark = pose.allPoseLandmarks
                            //Log.d(Constants.TAG, "Обработа позы")
                            if(allPoseLandmark.isNotEmpty()){

                                if(binding.parentLayout.childCount>3){
                                    binding.parentLayout.removeViewAt(3)
                                }

                                if(binding.parentLayout.childCount>3){
                                    binding.parentLayout.removeViewAt(3)
                                }

                                val element = Draw(applicationContext,pose)
                                binding.parentLayout.addView(element)
                            }
                            imageProxy.close()
                        }
                        .addOnFailureListener {
                            imageProxy.close()
                        }
                }
            }

            try {
                cameraProvaider.unbindAll()
                if (!poseDetectState) {
                    camera = cameraProvaider.bindToLifecycle(
                        this,
                        cameraSelector, imageCapture, preview, videoCapture
                    )  // imageCapture, imageAnalysis
                } else {
                    camera = cameraProvaider.bindToLifecycle(
                        this,
                        cameraSelector, imageCapture, preview, imageAnalysis
                    )  // imageCapture, imageAnalysis
                }
            //Log.d(Constants.TAG, "$cameraSelector")


            } catch (e: Exception) {
                Log.d(Constants.TAG, "ошибка запуска камеры", e )
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getOutputDir(): File {
        val mediaDir = externalMediaDirs.firstOrNull().let {mFile->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdir()
            }
        }
        return if(mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun getPermission() {
        var permissionList = mutableListOf<String>()
        Constants.REQUIRED_PERMISSIONS.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(it)
            }
        }
        if (permissionList.size > 0) {
            requestPermissions(permissionList.toTypedArray(), Constants.REQUEST_CODE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) {
                getPermission()
            }
        }
    }

}