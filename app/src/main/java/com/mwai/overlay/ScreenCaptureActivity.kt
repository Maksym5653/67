package com.mwai.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class ScreenCaptureActivity : Activity() {
    private val RC = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), RC)
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("data", data)
            })
            Toast.makeText(this, "✓ MW AI активовано!", Toast.LENGTH_SHORT).show()
        } else if (requestCode == RC) {
            Toast.makeText(this,
                "⚠ Потрібно натиснути 'Почати запис' для роботи аналізу екрану!",
                Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
