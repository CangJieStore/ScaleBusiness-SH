package cangjie.scale.business.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import cangjie.scale.business.R
import cangjie.scale.business.databinding.ActivityUploadImgBinding
import cangjie.scale.business.entity.UploadEvent
import cangjie.scale.business.service.UploadImgService
import com.cangjie.frame.core.BaseActivity
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * @author: guruohan
 * @date: 2021/11/21
 */
class UploadImgActivity : BaseActivity<ActivityUploadImgBinding>() {


    override fun layoutId(): Int = R.layout.activity_upload_img

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: UploadEvent) {
        if (event.code == 222) {
            Handler().postDelayed(Runnable {
                val lessSize = event.totalSize - event.waitSize
                val totalSize = event.totalSize
                val percent = (lessSize / totalSize * 1.0) * 100
                """${lessSize}/${totalSize}""".also {
                    mBinding.tvUploadSize.text = it
                }
                mBinding.pbUpload.progress = percent.toInt()
                """${percent.toInt()}%""".also { mBinding.tvPercent.text = it }
                if (lessSize == totalSize) {
                    mBinding.ivClose.visibility = View.VISIBLE
                }
            }, 50)
        }
    }

    override fun initActivity(savedInstanceState: Bundle?) {
        val orders = intent.getSerializableExtra("orders")
        val type = intent.getIntExtra("type", 0)
        mBinding.ivClose.visibility = if (type == 1) View.VISIBLE else View.GONE
        mBinding.ivClose.setOnClickListener {
            finish()
        }
        val bundle = Bundle()
        bundle.putSerializable("orders", orders)
        val intent = Intent(this@UploadImgActivity, UploadImgService::class.java)
        intent.putExtras(bundle)
        startService(intent)
    }

    override fun initImmersionBar() {
        immersionBar {
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }
}