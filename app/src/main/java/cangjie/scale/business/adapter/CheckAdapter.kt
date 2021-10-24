package cangjie.scale.business.adapter

import android.util.Log
import cangjie.scale.business.R
import cangjie.scale.business.databinding.LayoutCheckItemBinding
import cangjie.scale.business.entity.GoodsInfo
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseDataBindingHolder

/**
 * @author nvwa@cangjie
 * Create by AS at 2020/8/15 08:47
 */
class CheckAdapter :
    BaseQuickAdapter<GoodsInfo, BaseDataBindingHolder<LayoutCheckItemBinding>>(R.layout.layout_check_item) {

    private var dismissItem: GoodsInfo? = null

    override fun convert(holder: BaseDataBindingHolder<LayoutCheckItemBinding>, item: GoodsInfo) {
        holder.dataBinding?.let {
            it.tvOrderNo.text = (getItemPosition(item) + 1).toString()
            it.info = item
            holder.itemView.isSelected = item.isRepair
            it.calType =
                if (item.unit.contains("斤") || item.unit.contains("公斤") || item.unit.contains(
                        "千克"
                    ) || item.unit.contains("克") || item.unit.contains("两")
                ) {
                    "计重"
                } else {
                    "计数"
                }
            if (item.receive_loss == "1") {
                it.ivCalLoss.setImageResource(R.mipmap.iv_check_checked)
                it.ivCalLoss.isClickable = false
            } else {
                it.ivCalLoss.setImageResource(R.mipmap.iv_check_normal)
                it.ivCalLoss.isClickable = item.isRepair
            }

            dismissItem?.let { dis ->
                kotlin.run {
                    if (dis.id == item.id) {
                        if (dis.isLess == 1) {
                            it.ivCalLoss.setImageResource(R.mipmap.iv_check_checked)
                        } else {
                            it.ivCalLoss.setImageResource(R.mipmap.iv_check_normal)
                        }
                    }
                }
            }
        }
    }

    fun setDismissItem(item: GoodsInfo?) {
        this.dismissItem = item
        notifyDataSetChanged()
    }
}