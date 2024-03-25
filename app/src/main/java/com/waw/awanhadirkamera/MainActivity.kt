package com.waw.awanhadirkamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.util.Log
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.waw.awanhadirkamera.databinding.ActivityMainBinding
import com.waw.awanhadirkamera.utils.reduceFileImage
import com.waw.awanhadirkamera.utils.rotateBitmap
import java.io.*

class MainActivity : AppCompatActivity() {

    //URL Link
    internal var URL = "https://taspen.life"

    //for attach files
    private var mCameraPhotoPath: String? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var binding: ActivityMainBinding
    private var getFile: File? = null
    //    start here from awan
    private lateinit var webView: WebView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        loadingSpinner = binding.loading

        webViewComponent()

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION", "UNREACHABLE_CODE")
    private fun webViewComponent() {
        /** URL LINK  */
        webView.loadUrl(URL)
        /** Js  */
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.settings.javaScriptEnabled = true
        /** Location  */
        webView.settings.setGeolocationEnabled(true)
        webView.settings.setGeolocationDatabasePath(this.filesDir.path)
        /** Etc  */
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.domStorageEnabled = true
        webView.settings.setAppCacheEnabled(true)
        webView.settings.databaseEnabled = true

        //Setting WebViewClient
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Show loading spinner when page starts loading
                binding.root.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Hide loading spinner when page finishes loading
                binding.root.isRefreshing = false
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
            }

        }

        //Setting WebChromeClient -- (file attach request) --
        binding.webView.webChromeClient = object : WebChromeClient() {

            //> Get Camera For File Absensi
            @SuppressLint("QueryPermissionsNeeded")
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                if (ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.CAMERA
                    )!= PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity, arrayOf(Manifest.permission.CAMERA), CAMERA_X_RESULT
                    )
                }

                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                }
                mFilePathCallback = filePathCallback

                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent!!.resolveActivity(this@MainActivity.packageManager) != null) {
                    // Create the File Photo
                    var photoFile: File? = null
                    try {
                        photoFile = startPhoto()
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                        Log.e(TAG, "Unable to create Image File", ex)
                    }

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.absolutePath
                        takePictureIntent.putExtra(
                            MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(photoFile))
                    } else {
                        takePictureIntent = null
                    }

                    val intentArray: Array<Intent?> = if (takePictureIntent != null) {
                        arrayOf(takePictureIntent)
                    } else {
                        arrayOfNulls(0)
                    }

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, takePictureIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "Take Picture")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)

                    return true
                }
            }

            //> Get Lokasi For Absensi
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)
                // Request location permission if not granted
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity, // Replace with your activity reference
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                } else {
                    // Location permission already granted, invoke callback
                    callback?.invoke(origin, true, false)
                }
            }
        }

        //Setting Downloaded File
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)
            request.setDestinationInExternalFilesDir(
                this@MainActivity,
                Environment.DIRECTORY_DOWNLOADS,
                ".pdf"
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "Downloading File Successfully", Toast.LENGTH_LONG).show()
        }

        // Set up SwipeRefreshLayout
        binding.root.setOnRefreshListener { webView.reload() } // Refresh WebView content

        // Reload URL
        binding.webView.reload()

        // Request location permission
        requestLocationPermission()

        // Allow ALL Permission
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Location permission already granted
            webView.settings.setGeolocationEnabled(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                    this,
                    "Tidak mendapatkan permission.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startPhoto(): File? {
        val intent = Intent(this, CameraActivity::class.java)
        launcherIntentCameraX.launch(intent)
        if (getFile != null) {
            val file = reduceFileImage(getFile as File)
            getFile = file
        } else {
            Toast.makeText(
                this@MainActivity,
                "Silakan masukkan berkas gambar terlebih dahulu.",
                Toast.LENGTH_SHORT
            ).show()
        }
//        if (getFile != null) {
//            val file = reduceFileImage(getFile as File)
//            getFile = file
//        } else {
//            Toast.makeText(
//                this@MainActivity,
//                "Silakan masukkan berkas gambar terlebih dahulu.",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
        return getFile
    }


    private val launcherIntentCameraX = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CAMERA_X_RESULT) {
            val myFile = it.data?.getSerializableExtra("picture") as File
            val isFrontCamera = it.data?.getBooleanExtra("isBackCamera", true) as Boolean

            getFile = myFile
            val result = rotateBitmap(
                BitmapFactory.decodeFile(myFile.path),
                isFrontCamera
            )

           binding.previewImageView.setImageBitmap(result)
        }
    }

    companion object {
        const val CAMERA_X_RESULT = 200
        const val INPUT_FILE_REQUEST_CODE = 1
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val LOCATION_PERMISSION_REQUEST_CODE = 102
    }
}