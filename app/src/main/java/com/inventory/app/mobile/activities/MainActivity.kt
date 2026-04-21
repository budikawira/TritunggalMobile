package com.inventory.app.mobile.activities

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.inventory.app.mobile.R
import com.inventory.app.mobile.databinding.ActivityMainBinding
import com.inventory.app.mobile.fragments.BaseFragment
import com.rscja.deviceapi.RFIDWithUHFUART
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private lateinit var navController : NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    var currentFragment: BaseFragment? = null
    var mReader: RFIDWithUHFUART? = null
    var soundMap: HashMap<Int?, Int?> = HashMap<Int?, Int?>()
    private var soundPool: SoundPool? = null
    private var volumnRatio = 0f
    private var am: AudioManager? = null
    private var playSoundThread: PlaySoundThread? = null

    private var mypDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        navController = findNavController(this, R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration.Builder(navController.graph).build()
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        checkPermission()
        initSound()
        initUHF()
    }

    fun initUHF() {
        try {
            mReader = RFIDWithUHFUART.getInstance()
        } catch (ex: java.lang.Exception) {
            toastMessage(ex.message)

            return
        }

        if (mReader != null) {
            // Show progress dialog on the main thread
            mypDialog = ProgressDialog(this).apply { // Use requireContext() for Fragments
                setProgressStyle(ProgressDialog.STYLE_SPINNER)
                setMessage("init...")
                setCanceledOnTouchOutside(false)
                show()
            }

            // Launch a coroutine in the lifecycleScope of the Fragment/Activity
            // This coroutine will be cancelled when the Fragment/Activity is destroyed
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    // Perform the potentially long-running operation on a background thread (IO dispatcher)
                    // Assuming mReader.init returns a Boolean and requires a Context (like your original `this@MainActivity`)
                    // You might need to adjust `mReader.init` if it expects a specific Context type
                    // For a Fragment, use requireActivity() or requireContext()
                    // If mReader.init truly takes a MainActivity, then you'd need `activity as MainActivity`
                    // Let's assume it can take a standard Context
                    mReader?.init(applicationContext) // Replace with your actual mReader instance
                }

                // After the background task completes, switch back to the Main thread
                // to update UI and dismiss the dialog
                mypDialog?.dismiss() // Use dismiss() instead of cancel() for ProgressDialog

                if (result == null || !result) {
                    Toast.makeText(this@MainActivity, "init fail", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "init success", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toastMessage(msg: String?) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }


    private fun initSound() {
        soundPool = SoundPool(10, AudioManager.STREAM_MUSIC, 5)
        soundMap.put(1, soundPool!!.load(this, R.raw.barcodebeep, 1))
        soundMap.put(2, soundPool!!.load(this, R.raw.serror, 1))
        am = this.getSystemService(AUDIO_SERVICE) as AudioManager // 实例化AudioManager对象

        playSoundThread = PlaySoundThread()
        playSoundThread?.mainActivity = this
        playSoundThread?.start()
    }


    fun playSound(id: Int) {
        val audioMaxVolume =
            am!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() // 返回当前AudioManager对象的最大音量值
        val audioCurrentVolume =
            am!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() // 返回当前AudioManager对象的音量值
        volumnRatio = audioCurrentVolume / audioMaxVolume
        try {
            soundPool!!.play(
                soundMap.get(id)!!, volumnRatio,  // 左声道音量
                volumnRatio,  // 右声道音量
                1,  // 优先级，0为最低
                0,  // 循环次数，0不循环，-1永远循环
                1f // 回放速度 ，该值在0.5-2.0之间，1为正常速度
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSoundDelayed(speed: Int) {
        playSoundThread?.play(speed)
    }

    private class PlaySoundThread : Thread() {
        var mainActivity : MainActivity? = null
        private val objectLock = Any()
        private var isStop = false
        var interval: Int = 500
        var lastPlayTime: Long = SystemClock.elapsedRealtime()

        override fun run() {
            while (!isStop) {
                var start: Long = 0
                synchronized(objectLock) {
                    while (!isStop) {
                        if (start == 0L) {
                            start = SystemClock.elapsedRealtime()
                        } else {
                            if (SystemClock.elapsedRealtime() - start >= interval) {
                                break
                            } else {
                                SystemClock.sleep(1)
                            }
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastPlayTime < 500) {
                    mainActivity?.playSound(1)
                }
            }
        }

        fun play(speed: Int) {
            //speed 1-100;
            //100-1
            //99-10
            //98-20
            //97-30

            var t = 3
            if (speed > 85) {
                t = 3
            } else if (speed > 66) {
                t = 100 - speed
            } else if (speed > 33) {
                t = (100 - speed) * 2
            } else {
                t = (100 - speed) * 3
            }

            interval = t
            lastPlayTime = SystemClock.elapsedRealtime()
            // Log.i("UHFRadarLocationFrag", " interval=" + interval );
        }

        fun stopPlay() {
            isStop = true
            synchronized(objectLock) {
                (objectLock as Object).notifyAll()
            }
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        val currentDestination = navController.currentDestination
//        if (currentDestination?.id == R.id.homeFragment) {
            val inflater = menuInflater
            inflater.inflate(R.menu.main_menu, menu)
//        } else if (currentDestination?.id == R.id.historyFragment) {
//            val inflater = menuInflater
//            inflater.inflate(R.menu.history_menu, menu)
//        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_logout -> {
                //sessionManager.setLoginStatus(false)
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    fun showLoading(show : Boolean) {
        if (show) {
            binding.contentProgress.progress.visibility = View.VISIBLE
        } else {
            binding.contentProgress.progress.visibility = View.GONE
        }
    }


    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 139 || keyCode == 280 || keyCode == 293) {
            if (event.getRepeatCount() == 0) {
                if (currentFragment != null) {
                    currentFragment?.ReaderOnKeyDwon()
                }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

}