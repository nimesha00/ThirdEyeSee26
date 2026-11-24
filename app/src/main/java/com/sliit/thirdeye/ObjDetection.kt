package com.sliit.thirdeye

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sliit.thirdeye.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class ObjDetection : AppCompatActivity() {

    private lateinit var tts: TextToSpeech

    lateinit var labels: List<String>

    var colors = listOf<Int>(
        Color.BLUE,
        Color.GREEN,
        Color.RED,
        Color.CYAN,
        Color.GRAY,
        Color.BLACK,
        Color.DKGRAY,
        Color.MAGENTA,
        Color.YELLOW
    )

    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1

    // Set camera parameters
    private val focalLength = 26.0 // Replace with your camera's focal length
    private val imageSensorWidth =
        7.19 // Replace with your camera's image sensor width in millimeters
//    6.0

    private var person: Boolean = false
    private var bicycle: Boolean = false
    private var car: Boolean = false
    private var motorcycle: Boolean = false
    private var bus: Boolean = false
    private var truck: Boolean = false
    private var trafficlight: Boolean = false
    private var stopsign: Boolean = false
    private var cat: Boolean = false
    private var dog: Boolean = false
    private var cow: Boolean = false
    private var backpack: Boolean = false
    private var bottle: Boolean = false
    private var chair: Boolean = false
    private var couch: Boolean = false
    private var pottedPlant: Boolean = false
    private var diningTable: Boolean = false
    private var tv: Boolean = false
    private var laptop: Boolean = false
    private var mouse: Boolean = false
    private var remote: Boolean = false
    private var keyboard: Boolean = false
    private var cellPhone: Boolean = false
    private var refrigerator: Boolean = false
    private var book: Boolean = false
    private var clock: Boolean = false
    private var vase: Boolean = false

    // The model runs on its own thread. Running it on the UI thread for every camera
    // frame froze the screen: taps and the Back button took seconds to register.
    private lateinit var inferenceThread: HandlerThread
    private lateinit var inferenceHandler: Handler
    @Volatile private var inferenceBusy = false
    @Volatile private var ttsReady = false

    // If the previous screen is still releasing the camera, the first open can deliver
    // no frames at all (black screen). Reopen if nothing arrives within this time.
    @Volatile private var lastFrameAt = 0L
    private var reopenAttempts = 0
    private val noFrameTimeoutMs = 3000L
    private val maxReopenAttempts = 2

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obj_detection)

        get_permission();



        labels = FileUtil.loadLabels(this, "labels.txt")

        imageProcessor =
            ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        inferenceThread = HandlerThread("inferenceThread")
        inferenceThread.start()
        inferenceHandler = Handler(inferenceThread.looper)

        // One speech engine for the whole screen, instead of a new one per announcement.
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("en", "LK")
                tts.setSpeechRate(1f)
                ttsReady = true
                tts.speak(
                    "Live object detection is on. Press back to stop.",
                    TextToSpeech.QUEUE_FLUSH, null, "intro"
                )
            }
        }

        imageView = findViewById(R.id.imageView2)

        textureView = findViewById(R.id.textureView2)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                // Wait for the permission result instead of opening without permission.
                if (ContextCompat.checkSelfPermission(
                        this@ObjDetection, android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    open_camera()
                }
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
                // TODO("Not yet implemented")
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                lastFrameAt = android.os.SystemClock.elapsedRealtime()
                // Skip frames while the model is still working on the previous one.
                if (inferenceBusy || isFinishing || textureView.width == 0) return
                // Half size is plenty for a 300x300 model and much cheaper to copy on the UI thread.
                val frame = textureView.getBitmap(textureView.width / 2, textureView.height / 2) ?: return
                inferenceBusy = true
                inferenceHandler.post { runDetection(frame) }
            }

            fun runDetection(frame: Bitmap) {
                try {
                bitmap = frame
                // Creates inputs for reference.
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                // Runs model inference and gets result.
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width

                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f

                var x = 0

                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4


                    if (fl > 0.68) {
                        paint.setColor(colors.get(index % colors.size))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                locations.get(x + 3) * w,
                                locations.get(x + 2) * h
                            ), paint
                        )

                        val boudingBoxCenterX = (locations.get(x + 1) * w) + (300f / 2)

                        // Calculate distance to camera


                        if (labels.get(classes.get(index).toInt()) == "person") {

                            var distance = calculateDistanceToCamera(305f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )



                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(person){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                person = true
                            }


                        } else if(labels.get(classes.get(index).toInt()) == "bicycle"){

                            var distance = calculateDistanceToCamera(500f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(bicycle){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                bicycle = true;
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "car"){

                            var distance = calculateDistanceToCamera(1820f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(car){

                            }else {
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                car = true
                            }

                        }else if(labels.get(classes.get(index).toInt()) == "motorcycle"){



                            var distance = calculateDistanceToCamera(800f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(motorcycle){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                motorcycle=true
                            }



                        }else if(labels.get(classes.get(index).toInt()) == "bus"){

                            var distance = calculateDistanceToCamera(2250f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(bus){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                bus=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "truck"){

                            var distance = calculateDistanceToCamera(2450f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(truck){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                truck = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "traffic light"){

                            var distance = calculateDistanceToCamera(300f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(trafficlight){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                trafficlight = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "stop sign"){

                            var distance = calculateDistanceToCamera(600f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(stopsign){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                stopsign = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "cat"){

                            var distance = calculateDistanceToCamera(150f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(cat){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                cat = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "dog"){

                            var distance = calculateDistanceToCamera(250f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(dog){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                dog = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "cow"){

                            var distance = calculateDistanceToCamera(650f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(cow){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                cow = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "backpack"){

                            var distance = calculateDistanceToCamera(350f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(backpack){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                backpack=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "bottle"){

                            var distance = calculateDistanceToCamera(80f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(bottle){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                bottle = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "chair"){

                            var distance = calculateDistanceToCamera(450f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(chair){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                chair = true;
                            }

                        }else if(labels.get(classes.get(index).toInt()) == "couch"){

                            var distance = calculateDistanceToCamera(900f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(couch){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                couch = true;
                            }



                        }else if(labels.get(classes.get(index).toInt()) == "potted plant"){

                            var distance = calculateDistanceToCamera(130f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(pottedPlant){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                pottedPlant= true;
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "dining table"){

                            var distance = calculateDistanceToCamera(1350f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(diningTable){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                diningTable=true
                            }


                        } else if(labels.get(classes.get(index).toInt()) == "tv"){

                            var distance = calculateDistanceToCamera(1100f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(tv){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                tv=true;
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "laptop"){

                            var distance = calculateDistanceToCamera(350f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(laptop){
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                laptop=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "mouse"){

                            var distance = calculateDistanceToCamera(60f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(mouse){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                mouse=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "remote"){

                            var distance = calculateDistanceToCamera(50f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(remote){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                remote=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "keyboard"){

                            var distance = calculateDistanceToCamera(300f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(keyboard){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                keyboard=true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "cell phone"){

                            var distance = calculateDistanceToCamera(80f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(cellPhone){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                cellPhone = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "refrigerator"){

                            var distance = calculateDistanceToCamera(915f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                 "Door " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )
                            speakAIAssistant(
                                 "Door is " + distanceInInt.toString() + " feet in front of you"
                            )

                        }else if(labels.get(classes.get(index).toInt()) == "book"){

                            var distance = calculateDistanceToCamera(140f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false
                            vase = false

                            if(refrigerator){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                refrigerator = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "clock"){

                            var distance = calculateDistanceToCamera(80f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            clock = false
                            vase = false

                            if(book){

                            }else{
                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                book = true
                            }


                        }else if(labels.get(classes.get(index).toInt()) == "vase"){

                            var distance = calculateDistanceToCamera(100f, boudingBoxCenterX, 300)
                            val distanceInInt = (distance / 305).toInt()

                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + distanceInInt.toString() + "ft",
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )

                            person = false
                            bicycle = false
                            car = false
                            motorcycle = false
                            bus = false
                            truck = false
                            trafficlight = false
                            stopsign = false
                            cat = false
                            dog = false
                            cow = false
                            backpack = false
                            bottle = false
                            chair = false
                            couch = false
                            pottedPlant = false
                            diningTable = false
                            tv = false
                            laptop = false
                            mouse = false
                            remote = false
                            keyboard = false
                            cellPhone = false
                            refrigerator = false
                            book = false
                            clock = false

                            if(vase){

                            }else{

                                speakAIAssistant(
                                    labels.get(
                                        classes.get(index).toInt()
                                    ) + " is " + distanceInInt.toString() + " feet in front of you"
                                )
                                vase = true

                            }



                        } else {
                            paint.style = Paint.Style.FILL
                            canvas.drawText(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + " " + fl.toString(),
                                locations.get(x + 1) * w,
                                locations.get(x) * h,
                                paint
                            )



                            speakAIAssistant(
                                labels.get(
                                    classes.get(index).toInt()
                                ) + "is in front of you"
                            )
                        }

                        /*paint.style = Paint.Style.FILL
                        canvas.drawText(
                            labels.get(
                                classes.get(index).toInt()
                            ) + " " + fl.toString(),
                            locations.get(x + 1) * w,
                            locations.get(x) * h,
                            paint
                        )*/

                        /*speakAIAssistant(
                            labels.get(
                                classes.get(index).toInt()
                            ) + "is infront of you"
                        )*/

                    }
                }

                runOnUiThread {
                    if (!isDestroyed) imageView.setImageBitmap(mutable)
                    inferenceBusy = false
                }
                } catch (e: Exception) {
                    android.util.Log.e("ObjDetection", "Detection failed on a frame", e)
                    inferenceBusy = false
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager


    }



    private fun calculateDistanceToCamera(
        objectWidth: Float, boundingBoxCenterX: Float, imageWidth: Int
    ): Double {
        val distanceToCamera =
            (objectWidth * focalLength) / (boundingBoxCenterX * imageSensorWidth / imageWidth)
        return distanceToCamera
    }

    override fun onDestroy() {
        // Close the model on the inference thread itself, after the frame in progress.
        // Closing it from here while a frame was still running crashed the app (SIGSEGV).
        val modelToClose = model
        inferenceHandler.post { modelToClose.close() }
        inferenceThread.quitSafely()
        // Release the camera, or it can stay locked for the next screen that needs it.
        if (::cameraDevice.isInitialized) cameraDevice.close()
        handler.looper.quitSafely()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }


    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(
            cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0

                    var surfaceTexture = textureView.surfaceTexture
                    var surface = Surface(surfaceTexture)

                    var captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(
                        listOf(surface), object : CameraCaptureSession.StateCallback() {

                            override fun onConfigured(p0: CameraCaptureSession) {
                                p0.setRepeatingRequest(captureRequest.build(), null, null)
                                scheduleNoFrameCheck()
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                // TODO("Not yet implemented")
                            }
                        }, handler
                    )
                }

                override fun onDisconnected(p0: CameraDevice) {
                    // TODO("Not yet implemented")
                }

                override fun onError(p0: CameraDevice, p1: Int) {
                    // TODO("Not yet implemented")
                }
            }, handler
        )
    }

    /** Reopens the camera if no preview frame arrives soon after it starts streaming. */
    private fun scheduleNoFrameCheck() {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        textureView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (lastFrameAt < startedAt && reopenAttempts < maxReopenAttempts) {
                reopenAttempts++
                android.util.Log.w("ObjDetection", "No camera frames after ${noFrameTimeoutMs}ms; reopening (attempt $reopenAttempts)")
                if (::cameraDevice.isInitialized) cameraDevice.close()
                open_camera()
            }
        }, noFrameTimeoutMs)
    }

    fun get_permission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Re-requesting here loops forever once the user picks "Don't ask again".
            android.widget.Toast.makeText(
                this,
                "Object detection needs camera permission",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
        } else if (textureView.isAvailable) {
            // The surface may have become available while the dialog was showing.
            open_camera()
        }
    }

    // function for speaking
    // Called from the inference thread; TextToSpeech.speak is safe to call from any thread.
    private fun speakAIAssistant(text: String) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "detection")
        }
    }
}