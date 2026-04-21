package com.inventory.app.mobile.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.navigation.fragment.navArgs
import com.inventory.app.mobile.databinding.FragmentFindScanBinding
import com.rscja.deviceapi.entity.RadarLocationEntity
import com.rscja.deviceapi.interfaces.IUHF
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback
import kotlin.getValue

/**
 * A simple [androidx.fragment.app.Fragment] subclass.
 * Use the [FindScanFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FindScanFragment : BaseFragment() {
    companion object {
        private const val TAG = "FindScanFragment"
    }

    // Correct way to declare NavArgs
    private val args: FindScanFragmentArgs by navArgs()

    private var _binding : FragmentFindScanBinding? = null
    private val binding get() = _binding!!
    private val isSingleLabel = false
    private var inventoryFlag = false
    private var targetEpc: String? = null // 定位标签号

    //    private final RadarBackgroundView.StartAngle radarAngle = new RadarBackgroundView.StartAngle(0);
    //    private final List<RadarLocationEntity> radarTagList = new LinkedList<>();
    var progress: Int = 5
    lateinit var epc : String
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFindScanBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity?.currentFragment = this
        binding.seekBarPower.isEnabled = true
        binding.seekBarPower.progress = 5
        binding.btStart.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                startLocated()
            }
        })
        binding.btStop.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                stopLocated()
            }
        })
        epc = args.epc
        binding.etEPC.setText(epc)
    }

    @SuppressLint("LongLogTag")
    private fun startLocated() {
        if (inventoryFlag) return

        binding.radarView.clearPanel()
        binding.radarView.clearPanel()
        targetEpc = binding.etEPC.getText().toString()

        val result: Boolean? = mainActivity?.mReader?.startRadarLocation(
            mainActivity,
            targetEpc,
            IUHF.Bank_EPC,
            32,
            object : IUHFRadarLocationCallback {
                override fun getLocationValue(list: MutableList<RadarLocationEntity?>) {
//                Log.i(TAG, " list.size=" + list.size());
                    binding.radarView.bindingData(list, targetEpc)

                    //                mContext.playSound(1);
                    if (!TextUtils.isEmpty(targetEpc)) {
                        for (k in list.indices) {
                            //Log.i(TAG, " k=" + k + "  value=" + list.get(k).getValue());
                            if (list.get(k)!!.getTag() == targetEpc) {
                                mainActivity?.playSoundDelayed(list.get(k)!!.getValue())
                            }
                        }
                    } else {
                        mainActivity?.playSound(1)
                    }
                }

                override fun getAngleValue(angle: Int) {
                    /**Log.i(TAG, "angle=" + angle); */
                    binding.radarView.setRotation(-angle)
                }
            })
        if (result == null || !result) {
            mainActivity?.toastMessage("Fail to start!")
            //UIHelper.ToastMessage(mContext, "启动失败")
            return
        }

        binding.seekBarPower.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress2: Int, fromUser: Boolean) {
                Log.d(TAG, "  progress =" + progress2)
                progress = progress2
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "  onStartTrackingTouch")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p: Int = 35 - progress
                mainActivity?.mReader?.setDynamicDistance(p)
                Log.d(TAG, "  onStopTrackingTouch  p=" + p + "  progress=" + progress)
                //  Toast.makeText(getContext(),"功率："+progress,Toast.LENGTH_SHORT).show();
            }
        })
        binding.seekBarPower.setEnabled(true)
        inventoryFlag = true
        binding.btStart.setEnabled(false)
        binding.etEPC.setEnabled(false)

        binding.radarView.startRadar() // 启动雷达扫描动画
        Log.i(TAG, "startLocated success")
    }

    @SuppressLint("LongLogTag")
    private fun stopLocated() {
        if (!inventoryFlag) return
        binding.radarView.stopRadar() // 停止雷达扫描动画

        val result: Boolean? = mainActivity?.mReader?.stopRadarLocation()
        if (result == null || !result) {
            //停止失败
            Log.e(TAG, "stopLocated failure")
            mainActivity?.playSound(2)
            Toast.makeText(mainActivity, "Stop failure!", Toast.LENGTH_SHORT)
                .show()
        } else {
            Log.i(TAG, "stopLocated success")
            inventoryFlag = false
            binding.btStart.setEnabled(true)
            binding.etEPC.setEnabled(true)
        }
        binding.seekBarPower.setOnSeekBarChangeListener(null)
        binding.seekBarPower.progress = 5
        binding.seekBarPower.setEnabled(false)
    }


    public override fun onResume() {
        super.onResume()
    }

    public override fun onPause() {
        super.onPause()
        stopLocated()
        binding.radarView.stopRadar()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }


    override fun ReaderOnKeyDwon() {
        if (!inventoryFlag) {
            startLocated()
        } else {
            stopLocated()
        }
    }
}