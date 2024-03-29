package cangjie.scale.business.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import cangjie.scale.business.R
import cangjie.scale.business.adapter.OrderAdapter
import cangjie.scale.business.base.DateUtil
import cangjie.scale.business.base.workOnIO
import cangjie.scale.business.databinding.FragmentUncheckBinding
import cangjie.scale.business.entity.MessageEvent
import cangjie.scale.business.entity.OrderInfo
import cangjie.scale.business.vm.ScaleViewModel
import com.cangjie.frame.core.BaseMvvmFragment
import com.cangjie.frame.kit.show
import com.fondesa.recyclerviewdivider.dividerBuilder
import kotlinx.coroutines.launch

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


/**
 * @author nvwa@cangjie
 * Create by AS at 2020/8/13 17:25
 */
class UncheckFragment : BaseMvvmFragment<FragmentUncheckBinding, ScaleViewModel>() {

    private val orderAdapter by lazy {
        OrderAdapter(0)
    }

    override fun initFragment(view: View, savedInstanceState: Bundle?) {
        requireActivity().dividerBuilder()
            .color(Color.parseColor("#cccccc"))
            .size(1, TypedValue.COMPLEX_UNIT_DIP)
            .showLastDivider()
            .build()
            .addTo(mBinding!!.ryOrders)
        netTime()
        mBinding!!.adapter = orderAdapter
        orderAdapter.setOnItemClickListener { adapter, view, position ->
            val intent = Intent(requireActivity(), CheckActivity::class.java)
            val info = adapter.data[position] as OrderInfo
            intent.putExtra("id", info.trade_no)
            startActivity(intent)
        }
    }

    private fun netTime() {
        lifecycleScope.launch {
            workOnIO {
                try {
                    val infoUrl = URL("http://www.baidu.com")
                    val connection = infoUrl.openConnection()
                    connection.connect()
                    val ld = connection.date
                    val now = DateUtil.dateToString(Date(ld), DateUtil.DATE_FORMAT)
                    viewModel.loadMain(now)
                } catch (e: Exception) {
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
                    val date = Date(System.currentTimeMillis())
                    viewModel.loadMain(simpleDateFormat.format(date))
                }

            }
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MessageEvent) {
        if (event.code == 0) {
            viewModel.loadMain(event.msg)
        }
    }

    override fun initVariableId(): Int = cangjie.scale.business.BR.uncheckModel

    override fun layoutId(): Int = R.layout.fragment_uncheck
    override fun subscribeModel(model: ScaleViewModel) {
        super.subscribeModel(model)
        model.getOrderInfo().observe(this, Observer {
            it?.let {
                orderAdapter.setList(it.filter { it -> it.item_count != it.receive_item_count })
            }
        })
    }

    override fun toast(notice: String?) {
        super.toast(notice)
        show(requireActivity(), 2000, notice!!)
    }
}