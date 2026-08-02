package com.example.datamosh

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
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
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null
    private var prevFrame: ByteArray? = null

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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
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
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        try {
            createVideoFile()
            mediaRecorder = MediaRecorder().apply {
                camera?.unlock()
                setCamera(camera)
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(1280, 720)
                setVideoFrameRate(30)
                setOutputFile(videoFile?.absolutePath)
                setPreviewDisplay(surfaceView.holder.surface)
                prepare()
                start()
            }
            isRecording = true
            statusText.text = "🔴 Recording..."
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            camera?.lock()
            isRecording = false
            statusText.text = "⏹ Ready"
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Stop error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVideoFile() {
        val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        videoFile = File(storageDir, "DATAMOSH_$timeStamp.mp4")
    }

    private fun deleteRecording() {
        if (videoFile?.exists() == true) {
            videoFile?.delete()
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecording() {
        if (videoFile?.exists() == true) {
            Toast.makeText(this, "Saved: ${videoFile?.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No video", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        camera?.release()
    }
}
