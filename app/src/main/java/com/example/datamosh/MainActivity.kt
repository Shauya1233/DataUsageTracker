package com.example.datamosh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var recordButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var statusText: TextView
    
    private var camera: Camera? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var isRecording = false
    private var prevFrame: ByteArray? = null
    private var currentFrame: ByteArray? = null
    private var videoFile: File? = null
    private var previewCanvas: Canvas? = null
    private var previewBitmap: Bitmap? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 101
        private const val STORAGE_PERMISSION_CODE = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        recordButton = findViewById(R.id.recordButton)
        deleteButton = findViewById(R.id.deleteButton)
        saveButton = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)

        surfaceHolder = surfaceView.holder
        surfaceHolder?.addCallback(this)

        recordButton.setOnClickListener { toggleRecording() }
        deleteButton.setOnClickListener { deleteRecording() }
        saveButton.setOnClickListener { saveRecording() }

        checkPermissions()
        updateStatus()
    }

    private fun checkPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (cameraPermission != PackageManager.PERMISSION_GRANTED || 
            recordAudioPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                CAMERA_PERMISSION_CODE
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (storagePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            camera = Camera.open(0)
            camera?.setPreviewDisplay(holder)
            camera?.setPreviewCallback(this)
            camera?.startPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (holder.surface == null) return
        try {
            camera?.stopPreview()
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Preview error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (camera != null) {
            camera?.stopPreview()
            camera?.release()
            camera = null
        }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data == null || camera == null) return

        currentFrame = data
        
        if (isRecording && prevFrame != null) {
            // Apply datamoshing effect
            applyDatamosh(data)
        }

        prevFrame = data.copyOf()
        camera.addCallbackBuffer(data)
    }

    private fun applyDatamosh(frameData: ByteArray) {
        Thread {
            try {
                val params = camera?.parameters ?: return@Thread
                val previewSize = params.previewSize
                val width = previewSize.width
                val height = previewSize.height

                // Simple datamoshing: blend frames based on motion
                val result = ByteArray(frameData.size)
                
                for (i in frameData.indices step 4) {
                    val curr = frameData.getOrNull(i)?.toInt() ?: 0
                    val prev = prevFrame?.getOrNull(i)?.toInt() ?: 0
                    
                    // Calculate difference (motion detection)
                    val diff = abs(curr - prev)
                    
                    // Blend: if high motion, keep previous frame (moshing effect)
                    if (diff > 50) {
                        result[i] = (prev * 0.7 + curr * 0.3).toInt().toByte()
                    } else {
                        result[i] = frameData[i]
                    }
                }

                // Update preview with moshed frame
                runOnUiThread {
                    drawMoshedFrame(result, width, height)
                }
            } catch (e: Exception) {
                // Silently handle errors
            }
        }.start()
    }

    private fun drawMoshedFrame(frameData: ByteArray, width: Int, height: Int) {
        try {
            if (previewBitmap == null) {
                previewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            }

            previewBitmap?.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(frameData))
            previewCanvas = surfaceHolder?.lockCanvas() ?: return
            previewCanvas?.drawBitmap(previewBitmap!!, 0f, 0f, Paint())
            surfaceHolder?.unlockCanvasAndPost(previewCanvas)
        } catch (e: Exception) {
            // Handle drawing errors silently
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        updateStatus()
        createVideoFile()
        Toast.makeText(
            this,
            if (isRecording) "Recording started with datamoshing" else "Recording stopped",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun createVideoFile(): File {
        val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(storageDir, "DATAMOSH_$timeStamp.mp4")
        this.videoFile = videoFile
        return videoFile
    }

    private fun deleteRecording() {
        if (videoFile != null && videoFile!!.exists()) {
            videoFile!!.delete()
            videoFile = null
            updateStatus()
            Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No recording to delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecording() {
        if (videoFile != null && videoFile!!.exists()) {
            Toast.makeText(this, "Video saved: ${videoFile!!.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No recording to save", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        statusText.text = if (isRecording) {
            "🔴 Recording with Datamoshing..."
        } else {
            "⏹ Ready to record"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (camera != null) {
            camera?.stopPreview()
            camera?.release()
            camera = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                }
            }
        }
    }
}

