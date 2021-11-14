package cangjie.scale.business.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cangjie.scale.business.BR
import cangjie.scale.business.R
import cangjie.scale.business.base.workOnIO
import cangjie.scale.business.databinding.ActivityAdjustBinding
import cangjie.scale.business.scale.FormatUtil
import cangjie.scale.business.scale.ScaleModule
import cangjie.scale.business.scale.SerialPortUtilForScale
import cangjie.scale.business.vm.AdjustViewModel
import com.cangjie.frame.core.BaseMvvmActivity
import com.cangjie.frame.core.event.MsgEvent
import com.cangjie.frame.kit.show
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import kotlinx.coroutines.launch
import java.lang.Exception

/**
 * @author: guruohan
 * @date: 2021/11/14
 */
class AdjustActivity : BaseMvvmActivity<ActivityAdjustBinding, AdjustViewModel>() {
    override fun layoutId(): Int = R.layout.activity_adjust
    override fun initVariableId(): Int = BR.adjustModel
    private var readDataReceiver: AdjustActivity.ReadDataReceiver? = null
    private var mCurrentStep = 0
    private val mSteps = mutableListOf<TextView>()
    private val mTitles = mutableListOf<String>()
    private val mContents = mutableListOf<String>()
    private var scaleModule: ScaleModule? = null

    override fun initActivity(savedInstanceState: Bundle?) {
        initWeight()
    }

    private fun initWeight() {
        lifecycleScope.launch {
            workOnIO {
//                SerialPortUtilForScale.Instance().OpenSerialPort() //打开称重串口
                try {
                    scaleModule = ScaleModule.Instance(this@AdjustActivity) //初始化称重模块
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        show(this@AdjustActivity, 2000, "初始化称重主板错误！")
                    }
                }
            }
        }
        readDataReceiver = ReadDataReceiver()
        registerReceiver(
            readDataReceiver,
            IntentFilter(ScaleModule.WeightValueChanged)
        )
        registerReceiver(
            readDataReceiver,
            IntentFilter(ScaleModule.ERROR)
        )
        mSteps.add(mBinding.step1)
        mSteps.add(mBinding.step2)
        mSteps.add(mBinding.step3)
        mSteps.add(mBinding.step4)
        mTitles.add(resources.getString(R.string.check_title_1))
        mTitles.add(resources.getString(R.string.check_title_2))
        mTitles.add(resources.getString(R.string.check_title_3))
        mTitles.add(resources.getString(R.string.check_title_4))
        mContents.add(resources.getString(R.string.check_content_1))
        mContents.add(resources.getString(R.string.check_content_2))
        mContents.add(resources.getString(R.string.check_content_3))
        mContents.add(resources.getString(R.string.check_content_4))
        mBinding.checkNext.setOnClickListener {
            goNext()
        }
    }

    private fun goNext() {
        if (mCurrentStep == 3) {
            show(this, 2000, "校准完成")
            return
        }
        mSteps[mCurrentStep].setTextColor(resources.getColor(R.color.text_color))
        mCurrentStep++
        mSteps[mCurrentStep].setTextColor(resources.getColor(R.color.theme_color))
        mBinding.checkTitle.text = mTitles[mCurrentStep]
        mBinding.checkContent.text = mContents[mCurrentStep]
        setInputWeight()
        if (mCurrentStep == 3) {
            mBinding.checkNext.text = "完 成"
        } else {
            mBinding.checkNext.text = "下一步"
        }
        try {
            when (mCurrentStep) {
                1 -> {
                    scaleModule?.let { it.SetDeadLoadWeight() }
                }
                2 -> {}
                3 -> {
                    val fmNum = mBinding.checkWeightNum.text.toString().trim()
                    if (fmNum.isNotEmpty()) {
                        scaleModule!!.SetFullLoadWeight(
                            fmNum.toDouble()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            show(this, 2000, "校准失败")
            e.printStackTrace()
        }
    }

    private fun setInputWeight() {
        if (mCurrentStep == 2) {
            mBinding.checkIndication.visibility = View.GONE
            mBinding.checkWeight.visibility = View.VISIBLE
            mBinding.checkWeightNum.visibility = View.VISIBLE
        } else {
            mBinding.checkIndication.visibility = View.GONE
            mBinding.checkWeight.visibility = View.GONE
            mBinding.checkWeightNum.visibility = View.GONE
        }
    }

    override fun initImmersionBar() {
        super.initImmersionBar()
        immersionBar {
            fullScreen(true)
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            keyboardEnable(true)
            statusBarDarkFont(false)
            init()
        }
    }

    inner class ReadDataReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ScaleModule.ERROR == intent.action) {
                val error = intent.getStringExtra("error") as String

            } else {
                updateWeight()
            }
        }
    }

    private fun updateWeight() {
        try {
            val currentWeight = FormatUtil.roundByScale(
                ScaleModule.Instance(this@AdjustActivity).RawValue - ScaleModule.Instance(
                    this@AdjustActivity
                ).TareWeight,
                ScaleModule.Instance(this@AdjustActivity).SetDotPoint
            )
            mBinding.tvCurrentWeight.text = currentWeight
        } catch (ee: Exception) {
            ee.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readDataReceiver?.let {
            unregisterReceiver(it)
        }
//        SerialPortUtilForScale.Instance().CloseSerialPort()
    }

    override fun handleEvent(msg: MsgEvent) {
        super.handleEvent(msg)
        when (msg.code) {
            3 -> {
                finish()
            }
        }
    }
}