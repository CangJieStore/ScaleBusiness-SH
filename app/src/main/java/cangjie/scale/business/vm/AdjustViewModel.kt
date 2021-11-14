package cangjie.scale.business.vm

import com.cangjie.frame.core.binding.BindingAction
import com.cangjie.frame.core.binding.BindingCommand
import com.cangjie.frame.core.event.MsgEvent

/**
 * @author: guruohan
 * @date: 2021/11/14
 */
class AdjustViewModel : BaseScaleViewModel() {
    var finishCommand: BindingCommand<Any> = BindingCommand(object : BindingAction {
        override fun call() {
            action(MsgEvent(3))
        }
    })
}