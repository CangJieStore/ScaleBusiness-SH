package cangjie.scale.business.ui

import android.os.Bundle
import android.view.*
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import cangjie.scale.business.R
import cangjie.scale.business.databinding.LayoutCalLessBinding
import cangjie.scale.business.entity.GoodsInfo
import com.cangjie.frame.kit.keyboard.KeyboardNumberUtil
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.destroyImmersionBar
import com.gyf.immersionbar.ktx.immersionBar
import android.view.MotionEvent


/**
 * @author nvwa@cangjie
 * Create by AS at 2020/7/15 09:35
 */
class EditShellDialogFragment : DialogFragment() {


    private var layoutCalLessBinding: LayoutCalLessBinding? = null
    private var valueListener: LessValueListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.LossDialog)
    }

    override fun onStart() {
        super.onStart()
        immersionBar {
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            init()
        }
        dialog!!.setCanceledOnTouchOutside(false)
        val dialogWindow = dialog!!.window
        val lp = dialogWindow!!.attributes
        lp.height = WindowManager.LayoutParams.MATCH_PARENT
        val displayMetrics = requireContext().resources.displayMetrics
        lp.width = (displayMetrics.widthPixels * 0.4f).toInt()
        lp.gravity = Gravity.END
        dialogWindow.attributes = lp
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        layoutCalLessBinding =
            DataBindingUtil.inflate(inflater, R.layout.layout_cal_less, container, false)
        return layoutCalLessBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val info = arguments?.getSerializable("info") as GoodsInfo
        layoutCalLessBinding?.let {
            it.btnCancel.setOnClickListener {
                valueListener?.value(null, null)
                dismissAllowingStateLoss()
            }
            val keyboardUtil = KeyboardNumberUtil(requireActivity(), it.keyboardView)
            keyboardUtil.attachTo(it.editMatchCount)
            it.editMatchCount.setOnTouchListener { _, _ ->
                keyboardUtil.attachTo(it.editMatchCount)
                false
            }
            it.editReceivePrice.setOnTouchListener { _, _ ->
                keyboardUtil.attachTo(it.editReceivePrice)
                false
            }
            it.tvGoodsTitle.text = "商品名称:" + info.name
            it.tvGoodsSpec.text = "商品规格:" + info.spec
            it.tvBuyUnit2.text = info.unit
            if (info.is_sorting == 0) {
                it.editReceivePrice.isEnabled = true
                it.editMatchCount.isEnabled = true
            } else {
                it.editReceivePrice.setText(info.deliver_price)
                it.editMatchCount.setText(info.deliver_quantity)
                it.editReceivePrice.isEnabled = false
                it.editMatchCount.isEnabled = false
            }
            if (info.repair_receive == "1") {
                it.editReceivePrice.setText(info.deliver_price)
                it.editMatchCount.setText(info.deliver_quantity)
            }
            it.btnConfirm.setOnClickListener {
                valueListener?.value(
                    layoutCalLessBinding!!.editMatchCount.text.toString().trim(),
                    layoutCalLessBinding!!.editReceivePrice.text.toString().trim()
                )
                dismissAllowingStateLoss()
            }
        }
    }

    interface LessValueListener {
        fun value(count: String?, price: String?)
    }

    fun setValueListener(lis: LessValueListener): EditShellDialogFragment {
        this.valueListener = lis
        return this
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.let { destroyImmersionBar(it) }
    }

    companion object {
        fun newInstance(args: Bundle?): EditShellDialogFragment {
            val fragment = EditShellDialogFragment()
            if (args != null) {
                fragment.arguments = args
            }
            return fragment
        }
    }
}