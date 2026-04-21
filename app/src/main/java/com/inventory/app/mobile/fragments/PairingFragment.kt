package com.inventory.app.mobile.fragments

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.FragmentPairingBinding
import com.inventory.app.mobile.utils.Params
import com.inventory.app.mobile.utils.SessionManager
import com.inventory.app.mobile.utils.rest.ApiClient
import com.inventory.app.mobile.utils.rest.ApiInterface
import com.inventory.app.mobile.utils.rest.requests.BaseRequest
import com.inventory.app.mobile.utils.rest.requests.RegisterRfidRequest
import com.inventory.app.mobile.utils.rest.requests.RegisterRfidRecord
import com.inventory.app.mobile.utils.rest.response.BaseObjectResponse
import com.inventory.app.mobile.utils.rest.response.BaseResponse
import com.inventory.app.mobile.utils.rest.response.RegisterRfidResponse
import com.rscja.deviceapi.entity.UHFTAGInfo
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PairingFragment : BaseFragment() {
    companion object {
        private const val TAG = "PairingFragment"
    }
    private var _binding : FragmentPairingBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private var isCameraRunning = false

    enum class Status {
        Init,
        ScanRfid,
        ScanQR,
        UploadData
    }

    private val debugQr = arrayOf("25G0001","25G0002")
    private val debugEpc = arrayOf("32354130393936","32354130393939")
    private var debugCount = 0

    private var status : Status = Status.ScanRfid

    private var mIsScanning : Boolean = false

    private var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == 1) {
                val info = msg.obj as UHFTAGInfo
                Log.i("::handleMessage", "SoFragment.info=$info")
                val tid = info.tid
                val epc = info.epc
                val user = info.user
                Log.i(
                    ":Beka",
                    "UHFReadTagFragment.tid=" + tid + " epc=" + epc + " user=" + user + " info=" + info.rssi
                )
                updateScanData(epc)
            } else if (msg.what == 2) {
                this.removeMessages(2)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentPairingBinding.inflate(inflater, container, false)

        return binding.root
    }

    private fun updateStatus(status : Status) {
        this.status = status
        when (this.status) {
            Status.Init -> {
                binding.textQr.text = "-"
                binding.textRfid.text = "-"
                binding.textStatus.setText(R.string.scan_rfid_init)
                binding.previewView.visibility = View.INVISIBLE
                binding.overlayView.visibility = View.INVISIBLE
                binding.lottieRfid.visibility = View.INVISIBLE
                binding.lottieUpload.visibility = View.INVISIBLE
                binding.btnUpload.visibility = View.GONE
                binding.btnReset.visibility = View.GONE
                binding.btnScan.visibility = View.VISIBLE
                binding.btnScan.setText(R.string.start_scan)
                val color = ContextCompat.getColor(requireContext(), R.color.primary)
                binding.btnScan.backgroundTintList = ColorStateList.valueOf(color)
            }
            Status.ScanRfid -> {
                binding.textQr.text = "-"
                binding.textRfid.text = "-"
                binding.textStatus.setText(R.string.scan_rfid_tag)
                binding.previewView.visibility = View.INVISIBLE
                binding.overlayView.visibility = View.INVISIBLE
                binding.lottieRfid.visibility = View.VISIBLE
                binding.lottieUpload.visibility = View.INVISIBLE
                binding.btnUpload.visibility = View.GONE
                binding.btnReset.visibility = View.GONE
                binding.btnScan.visibility = View.VISIBLE
                binding.btnScan.setText(R.string.stop_scan)
                val color = ContextCompat.getColor(requireContext(), R.color.accent)
                binding.btnScan.backgroundTintList = ColorStateList.valueOf(color)

                //simulateScanRfid()
            }
            Status.ScanQR -> {
                binding.textStatus.setText(R.string.scan_qr_code)
                binding.previewView.visibility = View.VISIBLE
                binding.overlayView.visibility = View.VISIBLE
                binding.lottieUpload.visibility = View.INVISIBLE
                binding.lottieRfid.visibility = View.INVISIBLE
                binding.btnUpload.visibility = View.VISIBLE
                binding.btnReset.visibility = View.VISIBLE
                binding.btnScan.visibility = View.GONE
                binding.btnUpload.isEnabled = false
                binding.btnReset.isEnabled = true
                startCamera()
            }
            Status.UploadData -> {
                binding.previewView.visibility = View.INVISIBLE
                binding.overlayView.visibility = View.INVISIBLE
                binding.lottieUpload.visibility = View.VISIBLE
                binding.lottieRfid.visibility = View.INVISIBLE
                binding.textStatus.text = getString(R.string.upload_data)
                binding.btnUpload.isEnabled = true
                binding.btnReset.isEnabled = true
                stopCamera()
            }
            else -> {
                binding.textStatus.text = ""
            }
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isCameraRunning = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainActivity?.currentFragment = this
        sessionManager = SessionManager(mainActivity!!)
        ApiClient.setup(requireContext(), sessionManager.getServerUrl())
        apiInterface = ApiClient.client.create(ApiInterface::class.java)

        // Animate text color from primary to dark grey
        val colorAnim = ObjectAnimator.ofInt(
            binding.textStatus, "textColor",
            ContextCompat.getColor(mainActivity!!, R.color.primary),
            ContextCompat.getColor(mainActivity!!, R.color.darkGrey)
        )
        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient()

        colorAnim.duration = 1000 // 1 second
        colorAnim.setEvaluator(ArgbEvaluator()) // Smooth color transition
        colorAnim.repeatMode = ObjectAnimator.REVERSE
        colorAnim.repeatCount = ObjectAnimator.INFINITE

        colorAnim.start()

        updateStatus(Status.Init)

        binding.btnScan.setOnClickListener {
            if (!Params.DEBUG) {
                toggleScan()
            } else {
                simulateScanRfid()
            }
        }
        binding.btnReset.setOnClickListener {
            updateStatus(Status.Init)
        }

        binding.btnUpload.setOnClickListener {
            mainActivity?.showLoading(true)
            var params = RegisterRfidRequest()
            var rfid = binding.textRfid.text.toString()
            var qr = binding.textQr.text.toString()
            params.records.add(RegisterRfidRecord(rfid, qr))
            var request = apiInterface.registerRfid(params)
            request.enqueue(object : Callback<RegisterRfidResponse?> {
                override fun onResponse(
                    call: Call<RegisterRfidResponse?>,
                    response: Response<RegisterRfidResponse?>
                ) {
                    mainActivity?.showLoading(false)
                    var result = response.body()
                    if (result != null) {
                        if (result.result == BaseResponse.RESULT_OK && result.ok.size == 1) {
                            var error = result.message
                            if (error.isNullOrEmpty()) {
                                showMessage("Upload success, continue with next pairing?",
                                    SweetAlertDialog.SUCCESS_TYPE,
                                    {
                                        updateStatus(Status.Init)
                                    },
                                    {
                                        findNavController().popBackStack(findNavController().graph.startDestinationId,
                                            false)
                                    }, "Continue", "Main Menu"
                                    )
                            } else {
                                showMessage(error, SweetAlertDialog.ERROR_TYPE)
                            }
                        } else {

                            Toast.makeText(context, "Upload fail! Please try again!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Invalid response", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(
                    call: Call<RegisterRfidResponse?>,
                    t: Throwable
                ) {
                    mainActivity?.showLoading(false)
                    Toast.makeText(context, "Connection failure! " + t.message, Toast.LENGTH_SHORT).show()
                }

            })
        }

        binding.layoutPower.setOnClickListener {
            showPowerDialog()
        }
    }

    override fun onPowerUpdated() {
        super.onPowerUpdated()
        binding.textPower.text = "$radioPower dB"
    }


    private fun startCamera() {
        if (Params.DEBUG) {
            simulateScanQr()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(mainActivity!!)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis!!.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                isCameraRunning = true
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(mainActivity!!))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        Log.d("QR Scan", "QR Code Found: ${barcode.rawValue}")
                        // Handle scanned QR code result (e.g., show in UI)
                        qrCodeScanned(barcode.rawValue.toString())
                    }
                }
                .addOnFailureListener {
                    Log.e("QR Scan", "QR Code scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun qrCodeScanned(barcode : String) {
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                binding.textQr.text = barcode
                updateStatus(Status.UploadData)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

    }

    private fun toggleScan() {
        mIsScanning = !mIsScanning
        if (mIsScanning) {
            if (mainActivity?.mReader != null) {
                mainActivity?.mReader?.setInventoryCallback { uhftagInfo ->
                    val msg = handler.obtainMessage()
                    msg.obj = uhftagInfo
                    msg.what = 1
                    handler.sendMessage(msg)
                    mainActivity?.playSound(1)
                }
                mainActivity?.mReader?.power = radioPower
                var res = mainActivity?.mReader?.startInventoryTag()
                if (res != null && res) {
                    updateStatus(Status.ScanRfid)
                    handler.sendEmptyMessageDelayed(2, 10)
                } else {
                    stopInventory()
                    mIsScanning = false
                }
            }
        } else {
            stopInventory()
            updateStatus(Status.Init)
        }
    }

    private fun stopInventory() {
        if (mainActivity?.mReader != null) {
            mainActivity?.mReader?.stopInventory()
        } else {
            Toast.makeText(mainActivity, "Stop scaning inventory fail!", Toast.LENGTH_SHORT).show()
        }
    }
    override fun ReaderOnKeyDwon() {
        toggleScan()
    }

    private fun updateScanData(epc: String) {
        binding.textRfid.text = epc
        stopInventory()
        updateStatus(Status.ScanQR)
    }


    @kotlin.OptIn(DelicateCoroutinesApi::class)
    private fun simulateScanRfid() {
        updateStatus(Status.ScanRfid)
        GlobalScope.launch {
            delay(5000) // Pause for 5 seconds
            withContext(Dispatchers.Main) {
                binding.textRfid.text = debugEpc[debugCount]
                updateStatus(Status.ScanQR)
            }
        }
    }

    @kotlin.OptIn(DelicateCoroutinesApi::class)
    private fun simulateScanQr() {
        GlobalScope.launch {
            delay(5000) // Pause for 5 seconds
            withContext(Dispatchers.Main) {
                binding.textQr.text = debugQr[debugCount]
                debugCount++
                updateStatus(Status.UploadData)
            }
        }
    }
}