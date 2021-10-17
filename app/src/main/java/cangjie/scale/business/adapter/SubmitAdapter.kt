package cangjie.scale.business.adapter


import cangjie.scale.business.R
import cangjie.scale.business.databinding.LayoutSubmitItemBinding
import cangjie.scale.business.entity.SubmitInfo
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseDataBindingHolder

/**
 * @author nvwa@cangjie
 * Create by AS at 2020/8/16 14:18
 */
class SubmitAdapter :
    BaseQuickAdapter<SubmitInfo, BaseDataBindingHolder<LayoutSubmitItemBinding>>(R.layout.layout_submit_item) {
    override fun convert(holder: BaseDataBindingHolder<LayoutSubmitItemBinding>, item: SubmitInfo) {
        holder.dataBinding?.info = item
    }
}