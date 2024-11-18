package com.ttv.fingerdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ttv.palm.PalmEngine
import android.util.Log

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
    private val m_registerTemplates = mutableMapOf<String, ByteArray>()

    private var isSessionActive = false // To track session state
    private var sessionStartTimestamp: Long = 0L // To store session start time
    private val userTimestamps = mutableMapOf<String, Long>() // To store verification times for each user
    private val userScanStatus: MutableMap<String, String> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ret = PalmEngine.createInstance(this).init()
        if (ret != 0) {
            Toast.makeText(this, "PalmEngine not activated!", Toast.LENGTH_SHORT).show()
        }

        // Register Button
        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            if (checkAndRequestPermissions()) {
                openFingerCaptureActivity(0) // Mode 0: Register
            }
        }

        // Verify Button
        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            if (checkAndRequestPermissions()) {
                openFingerCaptureActivity(1) // Mode 1: Verify
            }
        }

        // Writer Register Button
        findViewById<Button>(R.id.btnWriterRegister).setOnClickListener {
            if (checkAndRequestPermissions()) {
                openFingerCaptureActivity(2) // Mode 2: Writer Register
            }
        }

        // Writer Verify Button
        findViewById<Button>(R.id.btnWriterVerify).setOnClickListener {
            if (checkAndRequestPermissions()) {
                openFingerCaptureActivity(3) // Mode 3: Writer Verify
            }
        }

        // Start/Stop Session Button
        val btnStartSession = findViewById<Button>(R.id.btnStartSession)
        btnStartSession.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

        btnStartSession.setOnClickListener {
            if (isSessionActive) {
                stopSession(btnStartSession)
            } else {
                startSession(btnStartSession)
            }
        }
    }

   private fun startSession(button: Button) {
    sessionStartTimestamp = System.currentTimeMillis() // Track session start time
    isSessionActive = true
    button.text = "Stop Session" // Update button text
    button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light)) // Set button color
    Toast.makeText(this, "Session started!", Toast.LENGTH_SHORT).show()
}

private fun stopSession(button: Button) {
    sessionStartTimestamp = 0L // Reset session start time
    isSessionActive = false
    button.text = "Start Session" // Update button text
    button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

    // Show session summary
    val summaryMessage = m_registerTemplates.keys.joinToString(separator = "\n") { user ->
        val status = userScanStatus[user] ?: "Not Scanned"
        "$user: $status"
    }

    Log.d("stopSession", "Session Summary: $summaryMessage")

    val builder = androidx.appcompat.app.AlertDialog.Builder(this)
    builder.setTitle("Session Summary")
    builder.setMessage(summaryMessage)
    builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
    builder.show()

    userScanStatus.clear() // Clear session data
    Toast.makeText(this, "Session stopped!", Toast.LENGTH_SHORT).show()
}

    // Open FingerCaptureActivity with the specified mode
    private fun openFingerCaptureActivity(mode: Int) {
        val intent = Intent(this, FingerCaptureActivity::class.java)
        intent.putExtra("mode", mode)
        startActivityForResult(intent, if (mode == 0 || mode == 2) 1 else 2)
    }

    // Check and Request Permissions
    private fun checkAndRequestPermissions(): Boolean {
    val permissionsToRequest = mutableListOf<String>()

    // Check for CAMERA permission
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.CAMERA)
    }

    // Check for WRITE_EXTERNAL_STORAGE permission for Android versions < R
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // Request permissions if not already granted
    return if (permissionsToRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), CAMERA_PERMISSION_REQUEST_CODE)
        false
    } else {
        true
    }
}


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission is required to proceed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (data == null) {
        Log.e("onActivityResult", "No data returned.")
        return
    }

    if (requestCode == 1 && resultCode == RESULT_OK) { // Register
        val registerID = data.getStringExtra("registerID")
        val palmFeature = data.getByteArrayExtra("palmFeature")
        Log.d("palmFeature", "palmFeature $palmFeature")
        if (registerID != null && palmFeature != null) {
            m_registerTemplates[registerID] = palmFeature
            Log.d("Registration", "User $registerID successfully registered.")
            Toast.makeText(this, "Register succeed! $registerID", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("Registration", "Failed to register user. Data missing.")
        }
    } else if (requestCode == 2 && resultCode == RESULT_OK) { // Verify
        val verifyResult = data.getIntExtra("verifyResult", 0)
        val verifyID = data.getStringExtra("verifyID")
        if (verifyResult == 1 && verifyID != null) {
            handleVerification(verifyID)
        } else {
            Toast.makeText(this, "Verify failed!", Toast.LENGTH_SHORT).show()
        }
    } else {
        Log.e("onActivityResult", "Unexpected requestCode or resultCode.")
    }
}

private fun handleVerification(userId: String) {
    val sessionStartTime = sessionStartTimestamp
    val currentTime = System.currentTimeMillis()

    // For future use: Uncomment this line to implement a timer (e.g., 2 hours)
    // val twoHoursInMillis = 2 * 60 * 60 * 1000 // 2 hours in milliseconds
    val twoHoursInMillis = 5 * 1000 // For testing: 5 seconds
    val scannedStatus: String

    Log.d("handleVerification", "Verifying userId: $userId")
    Log.d("handleVerification", "Current Session Start Time: $sessionStartTime")
    Log.d("handleVerification", "Current Time: $currentTime")
    Log.d("handleVerification", "Time Elapsed: ${currentTime - sessionStartTime} ms")

    // Focus on first verification logic
    if (!userScanStatus.containsKey(userId)) {
        // First verification
        scannedStatus = "First verification"
        showAlert("First Verification", "Welcome, user verified successfully!", true)
    } else {
        // Subsequent verification
        scannedStatus = "Already verified"
        showAlert("Duplicate Verification", "This user has already been verified!", false)
    }

    userScanStatus[userId] = scannedStatus
    Log.d("handleVerification", "Updated Scan Status: $userScanStatus")
}


    private fun showAlert(title: String, message: String, isSuccess: Boolean) {
    val builder = androidx.appcompat.app.AlertDialog.Builder(this)

    // Inflate custom layout
    val dialogView = layoutInflater.inflate(R.layout.custom_alert_dialog, null)
    builder.setView(dialogView)

    // Set background color based on success or failure
    val backgroundColor = if (isSuccess) android.R.color.holo_green_light else android.R.color.holo_red_light
    dialogView?.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))

    // Find and set title and message in custom layout
    val titleTextView = dialogView?.findViewById<android.widget.TextView>(R.id.alertTitle)
    val messageTextView = dialogView?.findViewById<android.widget.TextView>(R.id.alertMessage)
    titleTextView?.text = title
    messageTextView?.text = message

    // Create and show the dialog
    builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
    val dialog = builder.create()
    dialog.show()
}



}
