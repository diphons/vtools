package com.omarea.vboot

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.omarea.shared.*
import com.omarea.shell.Files
import com.omarea.shell.KeepShellSync
import com.omarea.shell.Platform
import com.omarea.shell.Props
import com.omarea.ui.ProgressBarDialog
import kotlinx.android.synthetic.main.layout_home.*
import java.io.File
import android.os.Debug.getMemoryInfo
import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import com.omarea.shell.cpucontrol.CpuFrequencyUtils
import java.util.*


class FragmentHome : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_home, container, false)
    }

    private var myHandler = Handler()
    private lateinit var globalSPF: SharedPreferences
    private fun showMsg(msg: String) {
        this.view?.let { Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show() }
    }

    private lateinit var spf: SharedPreferences
    private var modeList = ModeList()
    @SuppressLint("ApplySharedPref")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        globalSPF = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        if (Platform().dynamicSupport(context!!) || File(Consts.POWER_CFG_PATH).exists()) {
            powermode_toggles.visibility = View.VISIBLE
        } else {
            powermode_toggles.visibility = View.GONE
        }

        btn_powersave.setOnClickListener {
            installConfig(modeList.POWERSAVE, getString(R.string.power_change_powersave))
        }
        btn_defaultmode.setOnClickListener {
            installConfig(modeList.DEFAULT, getString(R.string.power_change_default))
        }
        btn_gamemode.setOnClickListener {
            installConfig(modeList.PERFORMANCE, getString(R.string.power_change_game))
        }
        btn_fastmode.setOnClickListener {
            installConfig(modeList.FAST, getString(R.string.power_chagne_fast))
        }

        spf = context!!.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        home_info_android.text = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        home_info_platform.text = Platform().GetCPUName()
        home_info_dynamic.text = if(Platform().dynamicSupport(context!!)) "支持" else (if(File(Consts.POWER_CFG_PATH).exists()) "自定义" else "不支持")
    }

    private var timer: Timer? = null
    override fun onResume() {
        super.onResume()
        setModeState()

        if (timer == null) {
            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    myHandler.post {
                        updateInfo()
                    }
                }
            }, 0, 3000)
        }
    }

    private fun cancel() {
        try {
            if (timer != null) {
                timer!!.cancel()
                timer = null
            }
        } catch (ex: Exception) {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    private fun updateInfo () {
        sdfree.text = "SDCard：" + Files.GetDirFreeSizeMB(Environment.getExternalStorageDirectory().absolutePath) + " MB"
        datafree.text = "Data：" + Files.GetDirFreeSizeMB(Environment.getDataDirectory().absolutePath) + " MB"
        val activityManager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        home_info_mem.text = "${(info.availMem / 1024 / 1024)} / ${(info.totalMem / 1024 / 1024)}MB"
        val swapInfo = KeepShellSync.doCmdSync("cat /proc/meminfo | grep Swap").split("\n")
        var totalSwap = "0"
        var freeSwap = "0"
        for (row in swapInfo) {
            if (row.startsWith("SwapTotal:")) {
                totalSwap = row.substring("SwapTotal:".length).trim()
            } else if(row.startsWith("SwapFree:")) {
                freeSwap = row.substring("SwapFree:".length).trim()
            }
        }
        home_info_swap.text = "${freeSwap} / ${totalSwap}"
        var gpuFreq = KeepShellSync.doCmdSync("cat /sys/class/kgsl/kgsl-3d0/clock_mhz")
        var gpuLoad = KeepShellSync.doCmdSync("cat /sys/class/kgsl/kgsl-3d0/devfreq/gpu_load")
        if (gpuFreq.isEmpty()) gpuFreq = "?"
        if (gpuLoad.isEmpty()) gpuLoad = "?"
        home_info_gpu.text = "${gpuFreq}Mhz ${gpuLoad}%"

        val coreCount = CpuFrequencyUtils.getCoreCount()
        home_info_cpu0.text = CpuFrequencyUtils.getCurrentFrequency(0)
        home_info_cpu1.text = CpuFrequencyUtils.getCurrentFrequency(1)
        if (coreCount > 2) {
            home_info_cpu2.text = CpuFrequencyUtils.getCurrentFrequency(2)
            home_info_cpu3.text = CpuFrequencyUtils.getCurrentFrequency(3)
        }
        if (coreCount > 4) {
            home_info_cpu4.text = CpuFrequencyUtils.getCurrentFrequency(4)
            home_info_cpu5.text = CpuFrequencyUtils.getCurrentFrequency(5)
        }
        if (coreCount > 6) {
            home_info_cpu6.text = CpuFrequencyUtils.getCurrentFrequency(6)
            home_info_cpu7.text = CpuFrequencyUtils.getCurrentFrequency(7)
        }
    }

    private fun setModeState() {
        btn_powersave.text = "省电"
        btn_defaultmode.text = "均衡"
        btn_gamemode.text = "性能"
        btn_fastmode.text = "极速"
        val cfg = Props.getProp("vtools.powercfg")
        when (cfg) {
            modeList.BALANCE -> btn_defaultmode.text = "均衡 √"
            modeList.PERFORMANCE -> btn_gamemode.text = "性能 √"
            modeList.POWERSAVE -> btn_powersave!!.text = "省电 √"
            modeList.FAST -> btn_fastmode!!.text = "极速 √"
        }
    }

    private fun installConfig(action: String, message: String) {
        if (spf.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CPU, false) && AccessibleServiceHelper().serviceIsRunning(this.context!!)) {
            AlertDialog.Builder(context)
                    .setTitle("")
                    .setMessage("检测到你已经开启“动态响应”，微工具箱将根据你的前台应用，自动调整CPU、GPU性能。\n如果你要更改全局性能，请先关闭“动态响应”！")
                    .setPositiveButton(R.string.btn_confirm, DialogInterface.OnClickListener { dialog, which ->
                    })
                    .show()
                    .create()
        }
        if (File(Consts.POWER_CFG_PATH).exists()) {
            modeList.executePowercfgModeOnce(action, context!!.packageName)
        } else {
            val stringBuilder = StringBuilder()
            stringBuilder.append(String.format(Consts.ToggleMode, action))
            ConfigInstaller().installPowerConfig(context!!, stringBuilder.toString());
        }
        setModeState()
        showMsg(message)
    }
}
