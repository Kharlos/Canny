package com.example.canny

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity() , CameraBridgeViewBase.CvCameraViewListener2 {
    private lateinit var cameraView: JavaCameraView
    private var processing = false
    private var blur = 5
    private var edgeThresh = 100
    private var angleVal = 90

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV successfully loaded.");
        }
        cameraView = findViewById(R.id.camera_view)
        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)

        findViewById<Button>(R.id.btn_start_stop).setOnClickListener {
            processing = !processing
            (it as Button).text = if (processing) "Stop" else "Start"
        }

        findViewById<SeekBar>(R.id.blur_slider).setOnSeekBarChangeListener(seekChange { blur = it })
        findViewById<SeekBar>(R.id.edge_slider).setOnSeekBarChangeListener(seekChange { edgeThresh = it })
        findViewById<SeekBar>(R.id.angle_slider).setOnSeekBarChangeListener(seekChange { angleVal = it })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableCam()
            } else {
                Log.e(TAG, "Permiso de cámara denegado")
            }
        }
    }

    private fun seekChange(action: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.e(TAG, "onCameraViewStarted")
    }
    override fun onCameraViewStopped() {

        Log.e(TAG, "onCameraViewStopped")
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        Log.e(TAG, "onCameraFrame")
        val rgba = inputFrame.rgba()
        if (!processing) return rgba

        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

        if (blur > 0 && blur % 2 == 1)
            Imgproc.GaussianBlur(gray, gray, Size(blur.toDouble(), blur.toDouble()), 0.0)

        val edges = Mat()
        Imgproc.Canny(gray, edges, edgeThresh.toDouble(), edgeThresh * 2.0)

        if (angleVal > 0 && angleVal % 2 == 1) {
            val gradX = Mat()
            val gradY = Mat()

            // Calcular gradientes (directamente en CV_32F)
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0)
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1)

            // Calcular ángulos (en grados)
            val angle = Mat()
            Core.phase(gradX, gradY, angle, true) // true = ángulos en grados

            // Crear máscara para el ángulo seleccionado (±10 grados)
            val angleMask = Mat()
            Core.inRange(
                angle,
                Scalar(angleVal - 10.0),
                Scalar(angleVal + 10.0),
                angleMask
            )

            // Aplicar máscara a los bordes detectados
            Core.bitwise_and(edges, edges, edges, angleMask)
        }
        val edgesColor = Mat()
        Imgproc.cvtColor(edges, edgesColor, Imgproc.COLOR_GRAY2RGBA) // Convertir a color

        // Combinar bordes con imagen original (30% transparente)
        Core.addWeighted(rgba, 0.7, edgesColor, 0.3, 0.0, rgba)
        // Convert to color to display over camera
        Imgproc.cvtColor(edges, rgba, Imgproc.COLOR_GRAY2RGBA)
        return rgba
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            enableCam()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun enableCam() {
        cameraView.setCameraPermissionGranted() // Para OpenCV 4.x+
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
        cameraView.enableView()
        Log.e(TAG, "enableView")
    }


    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }
}