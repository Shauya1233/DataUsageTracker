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

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var recordButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var statusText: TextView
    
    private var camera: Camera? = null
    private var isRecording = false
    private var videoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        recordButton = findViewById(R.id.recordButton)
        deleteButton = findViewById(R.id.deleteButton)
        saveButton = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)

        val holder = surfaceView.holder
        holder.addCallback(this)

        recordButton.setOnClickListener { toggleRecording() }
        deleteButton.setOnClickListener { deleteRecording() }
        saveButton.setOnClickListener { saveRecording() }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.release()
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        statusText.text = if (isRecording) "🔴 Recording..." else "⏹ Ready"
    }

    private fun deleteRecording() {
        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
    }

    private fun saveRecording() {
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }
}
