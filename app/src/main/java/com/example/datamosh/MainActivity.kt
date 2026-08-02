package com.example.datamosh

import android.Manifest
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var recordButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var statusText: TextView
    
    private var camera: Camera? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var isRecording = false
    private var videoFile: File? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 101
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
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            camera = Camera.open(0)
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (holder.surface == null) return
        try {
            camera?.stopPreview()
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Preview error", Toast.LENGTH_SHORT).show()
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
        if (data != null && camera != null) {
            if (isRecording) {
                // Recording logic here
            }
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        createVideoFile()
        updateStatus()
        Toast.makeText(this, if (isRecording) "Recording..." else "Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun createVideoFile(): File {
        val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(storageDir, "DATAMOSH_$timeStamp.mp4")
