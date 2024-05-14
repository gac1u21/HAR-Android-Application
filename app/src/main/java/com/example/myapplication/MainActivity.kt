/**
 * This file contains the main activity of the Android HAR application.
 * It manages sensor data recording, user interactions and HTTP requests.
 */

package com.example.myapplication
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * This class represents the main activity of the application.
 */
class MainActivity : Activity(), SensorEventListener {

    //Sensor Components
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val accelerometerData = mutableListOf<List<Float>>()
    private val gyroscopeData = mutableListOf<List<Float>>()

    //UI Components
    private lateinit var startButton: Button
    private lateinit var startContinuousButton: Button
    private lateinit var startLabelledRecordingButton: Button
    private lateinit var viewLog: Button
    private lateinit var statusTextView: TextView

    //Boolean values for recording options and activity log
    private val handler = Handler(Looper.getMainLooper())
    private var isRecording = false
    private var isContinuousRecording = false
    private var isLabelledRecording = false
    private var areRecordsShown = false

    //Runnable instances for sending sensor data to the server during recording intervals.
    private var sendDataRunnable: Runnable? = null
    private var sendContinuousDataRunnable: Runnable? = null
    private var sendLabelledDataRunnable: Runnable? = null

    @SuppressLint("SetTextI18n", "WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialise sensor manager and sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager //Manages access to device sensors.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) //Sensor object for accelerometer.
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) //Sensor object for gyroscope.

        //Buttons to control different recording modes.
        startButton = findViewById(R.id.startButton)
        startContinuousButton = findViewById(R.id.button_start_continuous_recording)
        startLabelledRecordingButton = findViewById(R.id.startLabelledRecordingButton)

        viewLog = findViewById(R.id.viewLog) //Button to toggle display of recorded data log.

        statusTextView = findViewById(R.id.statusTextView) //TextView object to display status messages.

        //Set onClickListeners for buttons
        startButton.setOnClickListener { toggleRecording() }
        startContinuousButton.setOnClickListener { toggleContinuousRecording() }
        startLabelledRecordingButton.setOnClickListener { toggleLabelledRecording() }

        //Initialise records and hide them
        val tvRecords = findViewById<TextView>(R.id.tvRecords)
        tvRecords.visibility = View.GONE
        val scrollView = findViewById<ScrollView>(R.id.scroll) //Initialise scroll view object

        //Set onClickListener for the activity log
        viewLog.setOnClickListener {
            if (areRecordsShown) {

                tvRecords.visibility = View.GONE
                viewLog.text = "Show Records"

                startButton.visibility = View.VISIBLE
                startContinuousButton.visibility = View.VISIBLE
                statusTextView.visibility = View.VISIBLE
                startLabelledRecordingButton.visibility = View.VISIBLE
                scrollView.apply {
                    scrollTo(0, 0)
                    requestLayout()
                }
            } else {
                val records = getSavedActivityRecords()
                tvRecords.text = if (records.isEmpty()) "No records found" else records
                tvRecords.visibility = View.VISIBLE
                viewLog.text = "Hide Records"

                startButton.visibility = View.GONE
                startContinuousButton.visibility = View.GONE
                startLabelledRecordingButton.visibility = View.GONE
                statusTextView.visibility = View.GONE
                scrollView.apply {
                    scrollTo(0, 0)
                    requestLayout()
                }
            }
            areRecordsShown = !areRecordsShown
        }
    }

    /**
     * Toggles recording state when the start button is clicked.
     */
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
            showAllButtons()
        } else {
            startRecording()
            hideAllButtonsExcept(startButton)
        }
    }

    /**
     * Toggles continuous recording state when the continuous recording button is clicked.
     */
    private fun toggleContinuousRecording() {
        if (isContinuousRecording) {
            stopContinuousRecording()
            showAllButtons()
        } else {
            startContinuousRecording()
            hideAllButtonsExcept(startContinuousButton)
        }
    }

    /**
     * Toggles labelled recording state when the labelled recording button is clicked.
     */
    private fun toggleLabelledRecording() {
        if (isLabelledRecording) {
            stopLabelledRecording()
            showAllButtons()
        } else {
            startLabelledRecording()
            hideAllButtonsExcept(startLabelledRecordingButton)
        }
    }

    /**
     * PLays a 3-second countdown sound.
     */
    private fun playCountdownSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.countdown_beep)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("Error", "Could not play countdown sound", e)
        }
    }

    /**
     * Starts the recording process.
     */
    @SuppressLint("SetTextI18n")
    private fun startRecording() {
        isRecording = true
        startButton.isEnabled = false
        playCountdownSound()

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusTextView.text = "Starting in ${millisUntilFinished / 1000 + 1}..."
            }

            override fun onFinish() {
                startButton.isEnabled = true
                isRecording = true
                statusTextView.text = "Recording..."
                startButton.text = "Stop Recording"

                accelerometerData.clear()
                gyroscopeData.clear()

                sensorManager.registerListener(this@MainActivity, accelerometer, 20000)
                sensorManager.registerListener(this@MainActivity, gyroscope, 20000)

                if (sendDataRunnable == null) {
                    sendDataRunnable = Runnable {
                        if (isRecording) {
                            sendSensorDataToServer()
                        }
                    }
                }
                handler.postDelayed(sendDataRunnable!!, 10500)
            }
        }.start()
    }

    /**
     * Starts continuous recording process.
     */
    @SuppressLint("SetTextI18n")
    private fun startContinuousRecording() {
        isContinuousRecording = true
        startContinuousButton.isEnabled = false
        playCountdownSound()

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusTextView.text = "Starting in ${millisUntilFinished / 1000 + 1}..."
            }

            override fun onFinish() {
                startContinuousButton.isEnabled = true
                isContinuousRecording = true
                statusTextView.text = "Recording..."
                startContinuousButton.text = "Stop Recording"

                accelerometerData.clear()
                gyroscopeData.clear()

                sensorManager.registerListener(this@MainActivity, accelerometer, 20000)
                sensorManager.registerListener(this@MainActivity, gyroscope, 20000)

                sendContinuousDataRunnable = Runnable {
                    if (isContinuousRecording) {
                        sendSensorDataToServer()
                        handler.postDelayed(sendContinuousDataRunnable!!, 10000)
                    }
                }
                handler.postDelayed(sendContinuousDataRunnable!!, 10500)
            }
        }.start()
    }

    /**
     * Starts labelled recording process.
     */
    @SuppressLint("SetTextI18n")
    private fun startLabelledRecording() {
        isLabelledRecording = true
        startLabelledRecordingButton.isEnabled = false
        playCountdownSound()

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusTextView.text = "Starting in ${millisUntilFinished / 1000 + 1}..."
            }

            override fun onFinish() {
                startLabelledRecordingButton.isEnabled = true
                isLabelledRecording = true
                statusTextView.text = "Recording..."
                startLabelledRecordingButton.text = "Stop Recording"

                accelerometerData.clear()
                gyroscopeData.clear()

                sensorManager.registerListener(this@MainActivity, accelerometer, 20000)
                sensorManager.registerListener(this@MainActivity, gyroscope, 20000)

                if (sendLabelledDataRunnable == null) {
                    sendLabelledDataRunnable = Runnable {
                        if (isLabelledRecording) {
                            stopAndPromptForLabel()
                        }
                    }
                }
                handler.postDelayed(sendLabelledDataRunnable!!, 10500)
            }
        }.start()
    }

    /**
     * Stops the recording process.
     */
    @SuppressLint("SetTextI18n")
    private fun stopRecording() {
        isRecording = false
        statusTextView.text = "Press the button to start recording"
        startButton.text = "Start Recording"

        sensorManager.unregisterListener(this)

        if (sendContinuousDataRunnable != null) {
            handler.removeCallbacks(sendContinuousDataRunnable!!)
        }
        if (sendDataRunnable != null) {
            handler.removeCallbacks(sendDataRunnable!!)
        }
        if (sendLabelledDataRunnable != null) {
            handler.removeCallbacks(sendLabelledDataRunnable!!)
        }
        showAllButtons()
    }

    /**
     * Stops continuous recording process.
     */
    @SuppressLint("SetTextI18n")
    private fun stopContinuousRecording() {
        isContinuousRecording = false
        statusTextView.text = "Press the button to start recording"
        startContinuousButton.text = "Start Continuous Recording"

        sensorManager.unregisterListener(this)

        if (sendDataRunnable != null) {
            handler.removeCallbacks(sendDataRunnable!!)
        }
        if (sendContinuousDataRunnable != null) {
            handler.removeCallbacks(sendContinuousDataRunnable!!)
        }
        showAllButtons()
    }

    /**
     * Stops labelled recording process and asks for label.
     */
    @SuppressLint("SetTextI18n")
    private fun stopAndPromptForLabel() {
        isLabelledRecording = false
        statusTextView.text = "Processing your data..."
        startLabelledRecordingButton.text = "Start Labelled Recording"
        playNotificationSound()

        val labelInput = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Label this activity")
            .setView(labelInput)
            .setCancelable(false)
            .setPositiveButton("Submit") { dialog, which ->
                val label = labelInput.text.toString().replace(" ", "")
                if (label.isNotEmpty()) {
                    sendLabelledSensorDataToServer(label)
                }
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
                if(!isContinuousRecording){
                    isRecording = false
                    startButton.text = "Start Recording"
                    statusTextView.text = "Press the button to start recording"

                    sensorManager.unregisterListener(this)

                    if (sendContinuousDataRunnable != null) {
                        handler.removeCallbacks(sendContinuousDataRunnable!!)
                    }
                    if (sendDataRunnable != null) {
                        handler.removeCallbacks(sendDataRunnable!!)
                    }
                    if (sendLabelledDataRunnable != null) {
                        handler.removeCallbacks(sendLabelledDataRunnable!!)
                    }
                    showAllButtons()
                }
            }
            .show()
    }

    /**
     * Stops labelled recording process without asking for label.
     */
    @SuppressLint("SetTextI18n")
    private fun stopLabelledRecording() {
        isLabelledRecording = false
        statusTextView.text = "Press the button to start recording"
        startLabelledRecordingButton.text = "Start Labelled Recording"

        sensorManager.unregisterListener(this)

        if (sendLabelledDataRunnable != null) {
            handler.removeCallbacks(sendLabelledDataRunnable!!)
        }
    }

    /**
     * Sends sensor data to server for recognition.
     */
    private fun sendSensorDataToServer() {
        Log.d("accel sensdata:", accelerometerData.toString())
        Log.d("gyro sensdata:", gyroscopeData.toString())
        val trimmedAccelerometerData = accelerometerData.takeLast(500)
        val trimmedGyroscopeData = gyroscopeData.takeLast(500)

        val requestBody = buildRequestBody(trimmedAccelerometerData, trimmedGyroscopeData)

        if(!isContinuousRecording) runOnUiThread {
            stopRecording()
        }
        AsyncTask.execute {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("http://192.168.1.62:5000/predict")
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val prediction = response.body?.string()
                    runOnUiThread {
                        statusTextView.text = prediction
                        playNotificationSound()

                        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val activityRecord = "$currentDateTime: $prediction"
                        saveActivityRecord(activityRecord)
                    }
                } else {
                    runOnUiThread {
                        statusTextView.text = "Error sending data: ${response.message}\nPlease try again"
                        playErrorSound()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    statusTextView.text = "Failed to send data: ${e.message}"
                    playErrorSound()
                }
            }
        }
    }

    /**
     * Sends sensor data to server for labelling.
     */
    private fun sendLabelledSensorDataToServer(activityLabel: String) {
        val trimmedAccelerometerData = accelerometerData.takeLast(500)
        val trimmedGyroscopeData = gyroscopeData.takeLast(500)
        Log.i("accelerometer data:", trimmedAccelerometerData.toString())
        Log.i("gyroscope data:", trimmedGyroscopeData.toString())

        val dataText = buildLabelledRequestBody(trimmedAccelerometerData, trimmedGyroscopeData, activityLabel)

        if(!isContinuousRecording) runOnUiThread {
            isRecording = false
            startButton.text = "Start Recording"

            sensorManager.unregisterListener(this)

            if (sendContinuousDataRunnable != null) {
                handler.removeCallbacks(sendContinuousDataRunnable!!)
            }
            if (sendDataRunnable != null) {
                handler.removeCallbacks(sendDataRunnable!!)
            }
            if (sendLabelledDataRunnable != null) {
                handler.removeCallbacks(sendLabelledDataRunnable!!)
            }
            showAllButtons()
        }

        AsyncTask.execute {
            val client = OkHttpClient.Builder()
                .connectTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://192.168.1.62:5000/upload_labeled_activity")
                .post(dataText)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val prediction = response.body?.string()
                    runOnUiThread {
                        statusTextView.text = prediction
                        playNotificationSound()

                        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val activityRecord = "$currentDateTime: $prediction"
                        saveActivityRecord(activityRecord)
                        showAllButtons()
                    }
                } else {
                    runOnUiThread {
                        statusTextView.text = "Error sending data: ${response.message}\nPlease try again"
                        playErrorSound()
                        showAllButtons()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    statusTextView.text = "Failed to send data: ${e.message}"
                    playErrorSound()
                    showAllButtons()
                }
            }
        }
    }

    /**
     * Handles sensor data changes and adds data to respective lists when the listener is triggered.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (!isRecording && !isContinuousRecording && !isLabelledRecording) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelerometerData.add(event.values.toList())
            Sensor.TYPE_GYROSCOPE -> gyroscopeData.add(event.values.toList())
        }
    }

    /**
     * Not used in this implementation.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    /**
     * Plays a notification sound.
     */
    private fun playNotificationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.notification_sound)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("Error", "Could not play notification sound", e)
        }
    }

    /**
     * Plays an error sound.
     */
    private fun playErrorSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.error_sound)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("Error", "Could not play notification sound", e)
        }
    }

    /**
     * Writes the text containing sensor data in .ts format for activity recognition.
     */
    private fun buildRequestBody(trimmedAccelerometerData: List<List<Float>>, trimmedGyroscopeData: List<List<Float>>): RequestBody {
        val dataText = buildString {
            append("@problemName SensorData\n")
            append("@timeStamps false\n")
            append("@missing false\n")
            append("@univariate false\n")
            append("@dimensions 6\n")
            append("@equalLength true\n")
            append("@seriesLength ").append(trimmedAccelerometerData.size).append("\n")
            append("@classLabel true StarJumps Squats\n")
            append("@data\n")

            val accelerometerX = trimmedAccelerometerData.map { it[0] }.joinToString(",")
            val accelerometerY = trimmedAccelerometerData.map { it[1] }.joinToString(",")
            val accelerometerZ = trimmedAccelerometerData.map { it[2] }.joinToString(",")

            val gyroscopeX = trimmedGyroscopeData.map { it[0] }.joinToString(",")
            val gyroscopeY = trimmedGyroscopeData.map { it[1] }.joinToString(",")
            val gyroscopeZ = trimmedGyroscopeData.map { it[2] }.joinToString(",")

            val data = "$accelerometerX:" +
                    "$accelerometerY:" +
                    "$accelerometerZ:" +
                    "$gyroscopeX:" +
                    "$gyroscopeY:" +
                    gyroscopeZ
            append(data).append(":Prediction")        }
        val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
        return dataText.toRequestBody(mediaType)
    }

    /**
     * Writes the text containing sensor data in .ts format for activity labelling.
     */
    private fun buildLabelledRequestBody(trimmedAccelerometerData: List<List<Float>>, trimmedGyroscopeData: List<List<Float>>, activityLabel: String): RequestBody {
        val dataText = buildString {

            val accelerometerX = trimmedAccelerometerData.map { it[0] }.joinToString(",")
            val accelerometerY = trimmedAccelerometerData.map { it[1] }.joinToString(",")
            val accelerometerZ = trimmedAccelerometerData.map { it[2] }.joinToString(",")

            val gyroscopeX = trimmedGyroscopeData.map { it[0] }.joinToString(",")
            val gyroscopeY = trimmedGyroscopeData.map { it[1] }.joinToString(",")
            val gyroscopeZ = trimmedGyroscopeData.map { it[2] }.joinToString(",")

            val data = "$accelerometerX:" +
                    "$accelerometerY:" +
                    "$accelerometerZ:" +
                    "$gyroscopeX:" +
                    "$gyroscopeY:" +
                    gyroscopeZ
            append(data).append(":$activityLabel")        }
        val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
        return dataText.toRequestBody(mediaType)
    }

    /**
     * Saves the performed activity in the history log with date and time.
     */
    private fun saveActivityRecord(record: String) {
        val sharedPreferences = getSharedPreferences("ActivityRecords", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val existingRecords = sharedPreferences.getString("records", "")

        val updatedRecords = if (existingRecords.isNullOrEmpty()) {
            record
        } else {
            "$existingRecords\n$record"
        }

        editor.putString("records", updatedRecords)
        editor.apply()
    }

    /**
     * Returns the string containing all activity records.
     */
    private fun getSavedActivityRecords(): String {
        val sharedPreferences = getSharedPreferences("ActivityRecords", Context.MODE_PRIVATE)
        return sharedPreferences.getString("records", "") ?: ""
    }

    /**
     * Hides all buttons except the one given as a parameter.
     */
    private fun hideAllButtonsExcept(exceptButton: Button) {
        val buttonsToHide = listOf(startButton, startContinuousButton, startLabelledRecordingButton, viewLog)
        buttonsToHide.forEach { button ->
            if (button != exceptButton) {
                button.visibility = View.GONE
            }
        }
    }

    /**
     * Shows all buttons.
     */
    private fun showAllButtons() {
        val allButtons = listOf(startButton, startContinuousButton, startLabelledRecordingButton, viewLog)
        allButtons.forEach { button ->
            button.visibility = View.VISIBLE
        }
    }
}
