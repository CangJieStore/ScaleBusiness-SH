package cangjie.scale.business.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import cangjie.scale.business.R
import cangjie.scale.business.databinding.ActivityLoginBinding
import cangjie.scale.business.scale.SerialPortUtilForScale
import cangjie.scale.business.vm.ScaleViewModel
import com.cangjie.frame.core.BaseMvvmActivity
import com.cangjie.frame.core.event.MsgEvent
import com.cangjie.frame.kit.show
import com.gyf.immersionbar.BarHide

import com.gyf.immersionbar.ktx.immersionBar
import kotlin.system.exitProcess

/**
 * @author: guruohan
 * @date: 2021/9/9
 */
class LoginActivity : BaseMvvmActivity<ActivityLoginBinding, ScaleViewModel>() {

    override fun initActivity(savedInstanceState: Bundle?) {
        mBinding.tvExit.setOnClickListener {
            SerialPortUtilForScale.Instance().CloseSerialPort()
            exitProcess(0)
        }
    }

    override fun initVariableId(): Int = cangjie.scale.business.BR.loginModel

    override fun layoutId(): Int = R.layout.activity_login
    override fun initImmersionBar() {
        immersionBar {
            fullScreen(true)
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            init()
        }
    }

    override fun toast(notice: String?) {
        super.toast(notice)
        show(this, 2000, notice!!)
    }


    override fun handleEvent(msg: MsgEvent) {
        super.handleEvent(msg)
        if (msg.code == 0) {
            startActivity(Intent(this, MainActivity::class.java))
            this@LoginActivity.finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        SerialPortUtilForScale.Instance().CloseSerialPort()
        exitProcess(0)
    }

}