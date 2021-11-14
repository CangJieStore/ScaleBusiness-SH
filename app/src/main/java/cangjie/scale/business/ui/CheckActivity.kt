package cangjie.scale.business.ui

import android.Manifest
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.view.marginStart
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cangjie.scale.business.R
import cangjie.scale.business.adapter.CheckAdapter
import cangjie.scale.business.adapter.CheckedAdapter
import cangjie.scale.business.adapter.ImageAdapter
import cangjie.scale.business.base.workOnIO
import cangjie.scale.business.databinding.ActivityCheckBinding
import cangjie.scale.business.db.SubmitOrder
import cangjie.scale.business.entity.GoodsInfo
import cangjie.scale.business.entity.OrderInfo
import cangjie.scale.business.entity.SubmitInfo
import cangjie.scale.business.scale.FormatUtil
import cangjie.scale.business.scale.ScaleModule
import cangjie.scale.business.scale.SerialPortUtilForScale
import cangjie.scale.business.vm.ScaleViewModel
import cangjie.scale.business.widget.CalLessPopView
import com.blankj.utilcode.util.ViewUtils
import com.cangjie.frame.core.BaseMvvmActivity
import com.cangjie.frame.core.clearText
import com.cangjie.frame.core.event.MsgEvent
import com.cangjie.frame.core.net.annotation.NetType
import com.cangjie.frame.kit.LuminosityAnalyzer
import com.cangjie.frame.kit.RotationListener
import com.cangjie.frame.kit.lib.ToastUtils
import com.cangjie.frame.kit.show
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemChildClickListener
import com.fondesa.recyclerviewdivider.dividerBuilder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.AttachPopupView
import com.lxj.xpopup.enums.PopupPosition
import com.lxj.xpopup.interfaces.OnSelectListener
import com.permissionx.guolindev.PermissionX
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.sp
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * @author nvwa@cangjie
 * Create by AS at 2020/8/14 22:23
 */
class CheckActivity : BaseMvvmActivity<ActivityCheckBinding, ScaleViewModel>() {

    private lateinit var outputDirectory: File
    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var sliderAppearingJob: Job? = null
    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private lateinit var cameraExecutor: ExecutorService
    private var readDataReceiver: ReadDataReceiver? = null

    private var currentGoodsInfo: GoodsInfo? = null
    private var submitList = arrayListOf<SubmitInfo>()
    private var currentDeliveryType = 0
    private var currentOrder: OrderInfo? = null
    private var orderID: String? = null
    private var date: String? = null
    private var currentShell: Float = 0.00F
    private var currentRepairGood: GoodsInfo? = null

    private val checkAdapter by lazy {
        CheckAdapter()
    }
    private val checkedAdapter by lazy {
        CheckedAdapter(object : CheckedAdapter.Action {
            override fun action(item: GoodsInfo) {
                currentRepairGood = item
                currentRepairGood!!.repair_receive = "1"
                viewModel.again(item.id)
            }
        })
    }

    private val imgAdapter by lazy {
        ImageAdapter()
    }
    private var imgData = arrayListOf<String>()

    override fun initActivity(savedInstanceState: Bundle?) {
        orderID = intent.getSerializableExtra("id") as String
        viewModel.loadDetail(orderID!!)
        date = intent.getStringExtra("date")
        initCamera()
        mBinding.editCurrentPrice.setOnClickListener {
            EditPriceDialogFragment("收货价格", "请输入...").setContentCallback(object :
                EditPriceDialogFragment.ContentCallback {
                override fun content(content: String?) {
                    mBinding.editCurrentPrice.text = content
                }
            }).show(supportFragmentManager)
        }
        mBinding.editCurrentCount.setOnClickListener {
            EditPriceDialogFragment("本次数量", "请输入...").setContentCallback(object :
                EditPriceDialogFragment.ContentCallback {
                override fun content(content: String?) {
                    mBinding.editCurrentCount.text = content
                }
            }).show(supportFragmentManager)
        }
        dividerBuilder()
            .color(Color.parseColor("#cccccc"))
            .size(1, TypedValue.COMPLEX_UNIT_DIP)
            .showLastDivider()
            .build()
            .addTo(mBinding.ryOrderCheck)
        dividerBuilder()
            .color(Color.parseColor("#cccccc"))
            .size(1, TypedValue.COMPLEX_UNIT_DIP)
            .showLastDivider()
            .build()
            .addTo(mBinding.ryOrderChecked)
        mBinding.adapterCheck = checkAdapter
        mBinding.adapterChecked = checkedAdapter
        checkAdapter.addChildClickViewIds(R.id.iv_cal_loss)
        checkAdapter.setOnItemClickListener { adapter, _, position ->
            val choosePosition = adapter.data[position] as GoodsInfo
            checkPosition(choosePosition)
            if (choosePosition != currentGoodsInfo) {
                currentShell = 0.0F
                updateWeight()
                returnZero()
                submitList.clear()
                imgData.clear()
                imgAdapter.data.clear()
                imgAdapter.notifyDataSetChanged()
                if (currentGoodsInfo!!.receive_loss != "1") {
                    currentGoodsInfo!!.isLess = 0
                    currentGoodsInfo!!.matchPrice = null
                    currentGoodsInfo!!.matchCount = null
                    currentGoodsInfo!!.costPrice = null
                    checkAdapter.setDismissItem(currentGoodsInfo)
                }
            }
            currentGoodsInfo = null
            currentGoodsInfo = choosePosition
            handlerSelected()
        }
        checkAdapter.setOnItemChildClickListener { adapter, view, position ->
            if (currentGoodsInfo!!.isLess == 0) {
                val popupView = XPopup.Builder(this@CheckActivity)
                    .autoOpenSoftInput(true)
                    .popupPosition(PopupPosition.Right)
                    .dismissOnTouchOutside(false)
                    .hasStatusBarShadow(false)
                    .asCustom(
                        CalLessPopView(
                            this@CheckActivity,
                            currentGoodsInfo!!,
                            object : CalLessPopView.LessValueListener {
                                override fun value(count: String?, price: String?) {
                                    immersionBar {
                                        hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
                                        init()
                                    }
                                    if (count.isNullOrEmpty() && price.isNullOrEmpty()) {
                                        currentGoodsInfo?.isLess = 0
                                        mBinding.llEditPrice.visibility = View.VISIBLE
                                        mBinding.tvReceivePrice.visibility = View.GONE
                                    } else {
                                        mBinding.llEditPrice.visibility = View.GONE
                                        mBinding.tvReceivePrice.visibility = View.VISIBLE
                                        checkAdapter.setDismissItem(currentGoodsInfo!!)
                                        currentGoodsInfo?.isLess = 1
                                        currentGoodsInfo?.matchCount = count!!
                                        currentGoodsInfo?.matchPrice = price!!
                                    }
                                    calCostPrice()
                                }
                            })
                    )
                popupView.show()
            } else {
                currentGoodsInfo!!.isLess = 0
                currentGoodsInfo!!.matchPrice = null
                currentGoodsInfo!!.matchCount = null
                currentGoodsInfo!!.costPrice = null
                checkAdapter.setDismissItem(currentGoodsInfo)
                mBinding.tvReceivePrice.visibility = View.GONE
                mBinding.llEditPrice.visibility = View.VISIBLE
                mBinding.editCurrentPrice.text = ""
            }
        }
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        mBinding.ryImg.layoutManager = linearLayoutManager
        mBinding.imgAdapter = imgAdapter
        imgAdapter.setOnItemClickListener { adapter, view, position ->
            val bundle = Bundle()
            bundle.putString("info", adapter.data[position] as String);
            PreviewCardDialogFragment.newInstance(bundle)!!
                .show(supportFragmentManager, "preview")
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
        mBinding.rgUnit.setOnCheckedChangeListener { p0, p1 ->
            val chosenUnit = currentGoodsInfo!!.units[p1]
            viewModel.currentUnit.set(chosenUnit)
            editCurrentDeliveryType(chosenUnit)
        }
    }

    private fun initWeight() {
        lifecycleScope.launch {
            workOnIO {
                SerialPortUtilForScale.Instance().OpenSerialPort() //打开称重串口
                try {
                    ScaleModule.Instance(this@CheckActivity) //初始化称重模块
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        show(this@CheckActivity, 2000, "初始化称重主板错误！")
                    }
                }
            }
        }
    }

    private fun calType(type: String): Int {
        return if (type.contains("斤")
            || type.contains("公斤")
            || type.contains("千克")
            || type.contains("克")
            || type.contains("两")
        ) {
            0
        } else {
            1
        }
    }

    private fun handlerSelected() {
        viewModel.currentUnit.set(currentGoodsInfo!!.unit)
        addUnit(currentGoodsInfo!!.units)
        mBinding.tvDeliveryPrice.text = "收货金额：" + currentGoodsInfo!!.receive_amount + "元"
        mBinding.tvDeliveryName.text = "商品名称：" + currentGoodsInfo!!.name
        mBinding.tvReceiveCount.text =
            "采购数量：" + currentGoodsInfo!!.deliver_quantity
        mBinding.tvDeliveryCount.text =
            "已验数量：" + currentGoodsInfo!!.receive_quantity
        if (currentGoodsInfo!!.receive_loss.isNotEmpty()) {
            currentGoodsInfo!!.isLess = currentGoodsInfo!!.receive_loss.toInt()
        }
        if (currentGoodsInfo!!.receive_price.isNotEmpty()) {
            if (currentGoodsInfo!!.receive_price.toFloat() > 0) {
                if (currentGoodsInfo!!.receive_loss == "1") {
                    mBinding.tvReceivePrice.visibility = View.VISIBLE
                    mBinding.llEditPrice.visibility = View.GONE
                    mBinding.tvReceivePrice.text = (currentGoodsInfo!!.receive_price)
                } else {
                    mBinding.tvReceivePrice.visibility = View.GONE
                    mBinding.llEditPrice.visibility = View.VISIBLE
                    mBinding.editCurrentPrice.text = currentGoodsInfo!!.receive_price
                }
            } else {
                mBinding.tvReceivePrice.visibility = View.GONE
                mBinding.llEditPrice.visibility = View.VISIBLE
                mBinding.editCurrentPrice.text = currentGoodsInfo!!.receive_price
            }
        }
        if (currentGoodsInfo!!.repair_receive == "1" && currentGoodsInfo!!.receive_loss == "1") {
            currentGoodsInfo!!.matchCount = currentGoodsInfo!!.deliver_quantity
            currentGoodsInfo!!.matchPrice = currentGoodsInfo!!.deliver_price
        }
        editCurrentDeliveryType(currentGoodsInfo!!.unit)
    }

    private fun editCurrentDeliveryType(unit: String) {
        if (calType(unit) == 0) {
            mBinding.tvDeliveryType.text = "计量方式：计重"
            mBinding.tvDeliveryCurrent.visibility = View.VISIBLE
            mBinding.tvDeliveryCurrent.text = mBinding.tvCurrentWeight.text.toString()
            mBinding.llEditCount.visibility = View.GONE
            currentDeliveryType = 1
            mBinding.btnRemove.visibility = View.VISIBLE
            mBinding.btnResetZero.visibility = View.VISIBLE
        } else {
            mBinding.tvDeliveryType.text = "计量方式：计数"
            currentDeliveryType = 2
            mBinding.tvDeliveryCurrent.visibility = View.GONE
            mBinding.tvCurrentWeight.text = "0.00"
            mBinding.editCurrentCount.text = ""
            mBinding.llEditCount.visibility = View.VISIBLE
            mBinding.btnRemove.visibility = View.GONE
            mBinding.btnResetZero.visibility = View.GONE
        }
    }

    override fun initVariableId(): Int = cangjie.scale.business.BR.checkModel

    override fun layoutId(): Int = R.layout.activity_check
    override fun initImmersionBar() {
        super.initImmersionBar()
        immersionBar {
            fullScreen(true)
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            statusBarDarkFont(false)
            init()
        }
    }

    override fun onStart() {
        super.onStart()
//        initWeight()
    }

    private fun formatUnit(currentWeight: String): String {
        if (currentGoodsInfo != null && currentWeight.isNotEmpty()) {
            return when (currentGoodsInfo!!.unit) {
                "斤" -> {
                    (FormatUtil.roundByScale(
                        currentWeight.toDouble() * 2 - currentShell,
                        2
                    )).toString()
                }
                "克" -> {
                    (FormatUtil.roundByScale(
                        currentWeight.toDouble() * 1000 - currentShell,
                        2
                    ))
                        .toString()
                }
                else -> {
                    currentWeight
                }
            }
        }
        return currentWeight
    }

    override fun handleEvent(msg: MsgEvent) {
        super.handleEvent(msg)
        when (msg.code) {
            3 -> {//finish
                finish()
            }
            4 -> {//restart delivery
                for (path in imgData) {
                    val file = File(path)
                    contentResolver.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Images.Media.DATA + "=?",
                        arrayOf(path)
                    )
                    file.delete()
                }
                currentRepairGood = currentGoodsInfo
                viewModel.clear(currentGoodsInfo!!.id)
                mBinding.tvDeliveryCount.text =
                    "已验数量：" + FormatUtil.roundByScale(
                        currentGoodsInfo!!.receive_quantity.toDouble(),
                        2
                    )
                mBinding.editCurrentCount.setText("")
                submitList.clear()
                imgData.clear()
                imgAdapter.data.clear()
                imgAdapter.notifyDataSetChanged()
            }
            5 -> {//submit delivery
                currentRepairGood = null
                if (mBinding.llEditCount.visibility == View.VISIBLE) {
                    if (submitList.size == 0) {
                        show(this@CheckActivity, 2000, "请拍照记录")
                        return
                    }
                }
                if (submitList.size == 0) {
                    show(this@CheckActivity, 2000, "请先验收才能完成提交")
                    return
                }
                if (currentGoodsInfo!!.isLess == 0) {
                    val price = mBinding.editCurrentPrice.text.toString()
                    if (price.isEmpty()) {
                        show(this@CheckActivity, 2000, "请输入收货价格完成提交")
                        return
                    } else {
                        if (price.toFloat() == 0f) {
                            show(this@CheckActivity, 2000, "请输入收货价格完成提交")
                            return
                        }
                    }

                }
                val bundle = Bundle()
                bundle.putSerializable("info", submitList)
                if (currentGoodsInfo!!.isLess == 0) {
                    submitList.forEach { da ->
                        run {
                            da.costPrice = mBinding.editCurrentPrice.text.toString()
                        }
                    }
                } else {
                    submitList.forEach { da ->
                        run {
                            da.costPrice = mBinding.tvReceivePrice.text.toString()
                        }
                    }
                }
                SubmitDialogFragment.newInstance(bundle)!!
                    .setAction(object : SubmitDialogFragment.SubmitAction {
                        override fun submit(
                            id: String,
                            count: String,
                            isLess: Int,
                            dCount: String,
                            dPrice: String,
                            cPrice: String,
                            unit: String
                        ) {
                            viewModel.submit(
                                id,
                                count,
                                isLess,
                                cPrice,
                                dCount,
                                dPrice,
                                currentGoodsInfo!!.repair_receive, unit
                            )
                        }
                    }).show(supportFragmentManager, "submit")

            }
            7 -> {//take photo
                if (currentDeliveryType == 2) {
                    if (TextUtils.isEmpty(mBinding.editCurrentCount.text.toString())) {
                        if (mBinding.editCurrentCount.text.toString().isEmpty()) {
                            show(this@CheckActivity, 2000, "请输入验收数量")
                            return
                        }
                    }
                }
                val currentNum = deliveryCount()
                takePhoto(currentNum)
            }
            200 -> {//submit response
                for (item in submitList) {
                    viewModel.add(
                        SubmitOrder(
                            goodsId = currentGoodsInfo!!.id,
                            batchId = item.batch,
                            batchPath = item.batch_path,
                            isUpload = 1
                        )
                    )
                }
                viewModel.loadDetail(orderID!!)
                toast("提交成功")
                submitList.clear()
                checkAdapter.setDismissItem(null)
                imgData.clear()
                imgAdapter.data.clear()
                imgAdapter.notifyDataSetChanged()
            }
            300 -> {
                viewModel.showStatusFiled.set(0)
                viewModel.loadDetail(orderID!!)
            }
            119 -> {
                showShell()
            }
            223 -> {
                viewModel.loadDetail(orderID!!)
                currentGoodsInfo!!.receive_quantity = "0.00"
                currentGoodsInfo!!.batch = ""
                mBinding.tvDeliveryCount.text =
                    "已验数量：0.00"
                checkedAdapter.remove(currentGoodsInfo!!)
                checkedAdapter.notifyDataSetChanged()

            }
            700 -> {
                currentShell = 0.0f
                returnZero()
                updateWeight()
            }
        }
    }

    private fun showShell() {
        EditShellDialogFragment.newInstance(null)!!
            .setAction(object : EditShellDialogFragment.SubmitAction {
                override fun submit(shell: String) {
                    currentShell = shell.toFloat()
                    updateWeight()
                }
            }).show(supportFragmentManager, "shell")
    }

    override fun toast(notice: String?) {
        super.toast(notice)
        show(this, 2000, notice!!)
    }

    private fun initCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        displayManager.registerDisplayListener(displayListener, null)
        outputDirectory = getOutputDirectory(this)
        mBinding.previewView.post {
            displayId = mBinding.previewView.display.displayId
            updateCameraUi()
            bindCameraUseCases()
        }
        setUpScreenRotationListener()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(p0: Int) {
            if (displayId == this@CheckActivity.displayId) {
                imageCapture?.targetRotation = mBinding.root.display.rotation
                imageAnalyzer?.targetRotation = mBinding.root.display.rotation
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private fun setUpScreenRotationListener() {
        val rotationListener = object : RotationListener(this) {
            override fun onSimpleOrientationChanged(orientation: Int) {
                when (orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                    }
                    Configuration.ORIENTATION_PORTRAIT -> {
                    }
                }
            }
        }
        rotationListener.enable()
    }


    override fun onDestroy() {
        super.onDestroy()
        readDataReceiver?.let {
            unregisterReceiver(it)
        }
//        SerialPortUtilForScale.Instance().CloseSerialPort()
        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)
        sliderAppearingJob?.cancel()
    }

    private fun bindCameraUseCases() {
        val metrics = DisplayMetrics().also { mBinding.previewView.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        val rotation = mBinding.previewView.display.rotation
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            preview?.setSurfaceProvider(mBinding.previewView.surfaceProvider)

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luminosityLevel ->
                    })
                }

            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateCameraUi() {
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                file == null
            }.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }
    }

    private fun returnZero() {
        try {
            ScaleModule.Instance(this).ZeroClear()
        } catch (e: Exception) {
            ViewUtils.runOnUiThread {
                ToastUtils.show("置零失败,重新初始化连接...")
            }
        }
    }

    private fun takePhoto(currentWeight: String) {
        loading("处理中...")
        imageCapture?.let { imageCapture ->

            val photoFile = createFile(
                outputDirectory,
                getString(R.string.output_photo_date_template),
                getString(R.string.output_photo_ext)
            )
            val metadata = ImageCapture.Metadata().apply {
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        val createTimeSdf1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        var reBatch = 0
                        if (currentGoodsInfo!!.batch.isNotEmpty()) {
                            reBatch = currentGoodsInfo!!.batch.toInt() + 1
                        }
                        val labels: MutableList<String> =
                            ArrayList()
                        labels.add("订单编号:" + currentOrder!!.trade_no)
                        labels.add("验收时间:" + createTimeSdf1.format(Date()))
                        labels.add("商品名称:" + currentGoodsInfo!!.name)
                        if (currentGoodsInfo!!.isLess == 1) {
                            labels.add("采购数量:" + currentGoodsInfo!!.matchCount + viewModel.currentUnit.get())
                        } else {
                            labels.add("采购数量:" + currentGoodsInfo!!.deliver_quantity + viewModel.currentUnit.get())
                        }
                        labels.add("验收批次:" + (imgData.size + reBatch + 1).toString())
                        labels.add("本批数量:" + currentWeight + viewModel.currentUnit.get())
                        makeWater(photoFile, labels, imgData.size + reBatch, currentWeight)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            setGalleryThumbnail(savedUri)
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            sendBroadcast(
                                Intent(
                                    android.hardware.Camera.ACTION_NEW_PICTURE,
                                    savedUri
                                )
                            )
                        }

                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(savedUri.toFile().extension)
                        MediaScannerConnection.scanFile(
                            this@CheckActivity,
                            arrayOf(savedUri.toString()),
                            arrayOf(mimeType)
                        ) { _, uri ->
                        }
                    }
                })
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun setGalleryThumbnail(uri: Uri) {

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }

    private fun createFile(baseFolder: File, format: String, extension: String) =
        File(
            baseFolder,
            getString(
                R.string.output_photo_name_template,
                SimpleDateFormat(
                    format,
                    Locale.CHINA
                ).format(System.currentTimeMillis()) + extension
            )
        )

    private fun createWaterFile(baseFolder: File, format: String, extension: String) =
        File(
            baseFolder,
            getString(
                R.string.output_photo_water_template,
                SimpleDateFormat(
                    format,
                    Locale.CHINA
                ).format(System.currentTimeMillis()) + extension
            )
        )

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(
                    it,
                    appContext.resources.getString(R.string.output_photo_directory)
                ).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists()) mediaDir else appContext.filesDir
        }
    }

    private fun makeWater(inputPath: File, labels: List<String>, batchID: Int, cWeight: String) {
        val single: Single<String> = Single.create { emitter ->
            val oldBitmap = BitmapFactory.decodeStream(FileInputStream(inputPath))
            val waterBitmap =
                addTimeFlag(this@CheckActivity, labels, 0, 12, "#ffffff", oldBitmap)
            val file = createWaterFile(
                outputDirectory,
                getString(R.string.output_photo_date_template),
                getString(R.string.output_photo_ext)
            )
            val os: OutputStream
            var ret = false
            try {
                os = BufferedOutputStream(FileOutputStream(file))
                ret = waterBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, os)
                waterBitmap.recycle()
                os.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (ret) {
                emitter.onSuccess(file.path)
            }
        }
        single.subscribe(object : SingleObserver<String> {
            override fun onSuccess(path: String) {
                dismissLoading()
                inputPath.delete()
                submitList.add(
                    SubmitInfo(
                        currentGoodsInfo!!.id,
                        (batchID + 1).toString(),
                        cWeight,
                        path,
                        currentGoodsInfo!!.name,
                        (if (currentGoodsInfo!!.isLess == 1) currentGoodsInfo!!.matchCount else currentGoodsInfo!!.deliver_quantity),
                        currentGoodsInfo!!.deliver_quantity,
                        viewModel.currentUnit.get()!!,
                        currentGoodsInfo!!.receive_quantity,
                        currentGoodsInfo!!.isLess,
                        currentGoodsInfo!!.matchCount,
                        currentGoodsInfo!!.matchPrice,
                        (if (currentGoodsInfo!!.isLess == 1) mBinding.tvReceivePrice.text.toString() else mBinding.editCurrentPrice.text.toString())
                    )
                )
                imgData.add(path)
                runOnUiThread {
                    calCostPrice()
                    mBinding.editCurrentCount.text = ""
                    mBinding.tvDeliveryCount.text =
                        "已验数量：" + getDeliveryCount()
                    imgAdapter.setList(imgData)
                    mBinding.ryImg.smoothScrollToPosition(imgAdapter.itemCount - 1)
                }
            }

            override fun onSubscribe(d: Disposable) {

            }

            override fun onError(e: Throwable) {
            }
        })
    }


    private fun deliveryCount(): String {
        return if (currentDeliveryType == 1) {
            mBinding.tvCurrentWeight.text.toString()
        } else {
            mBinding.editCurrentCount.text.toString()
        }
    }

    private fun getDeliveryCount(): String {
        var count = 0.00
        if (currentGoodsInfo != null) {
            count += currentGoodsInfo!!.receive_quantity.toDouble()
            for (item in submitList) {
                if (!TextUtils.isEmpty(item.batch_count)) {
                    count += item.batch_count.toFloat()
                }
            }
        }
        return FormatUtil.roundByScale(count, 2).toString()
    }

    private fun calCostPrice() {
        if (currentGoodsInfo!!.isLess == 1) {
            val count = currentGoodsInfo!!.matchCount
            val price = currentGoodsInfo!!.matchPrice
            val deliveryCount = getDeliveryCount()
            if (!count.isNullOrEmpty() && !price.isNullOrEmpty()) {
                val finalPrice = (count.toFloat() * price.toFloat()) / deliveryCount.toFloat()
                mBinding.tvReceivePrice.visibility = View.VISIBLE
                mBinding.llEditPrice.visibility = View.GONE
                if (deliveryCount.toFloat() > 0) {
                    mBinding.tvReceivePrice.text = String.format("%.2f", finalPrice)
                } else {
                    mBinding.tvReceivePrice.text = "0.00"
                }
            }
        }

    }

    private fun addTimeFlag(
        context: Context,
        labels: List<String>,
        degress: Int,
        fontSize: Int,
        fontColor: String,
        src: Bitmap
    ): Bitmap? {
        val width = src.width
        val height = src.height
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint()
        paint.color = Color.parseColor(fontColor)
        paint.isAntiAlias = true
        paint.textSize = sp2px(context, fontSize.toFloat()).toFloat()
        paint.alpha = 220
        canvas.save()
        canvas.rotate(degress.toFloat())
        var spacing = 0
        for (label in labels) {
            canvas.drawText(label, 20f, 35f + spacing.toFloat(), paint)
            spacing += 25
        }
        canvas.restore()
        return newBitmap
    }

    private fun sp2px(context: Context, spValue: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F1 || keyCode == KeyEvent.KEYCODE_F3) {//置零
            currentShell = 0.0F
            returnZero()
            updateWeight()
        } else if (keyCode == KeyEvent.KEYCODE_Y) {
            showShell()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun subscribeModel(model: ScaleViewModel) {
        super.subscribeModel(model)
        model.currentOrder.observe(this, {
            it?.let {
                currentOrder = it
                mBinding.info = currentOrder
                checkedAdapter.setList(it.goods.filter { value -> value.receive_quantity.toFloat() > 0f })
                if (it.goods.size > 0) {
                    if (currentRepairGood != null) {
                        currentGoodsInfo = currentRepairGood
                        currentGoodsInfo!!.repair_receive = currentRepairGood!!.repair_receive
                        currentGoodsInfo!!.matchCount = currentGoodsInfo!!.deliver_quantity
                        currentGoodsInfo!!.matchPrice = currentGoodsInfo!!.deliver_price
                        it.goods.forEach { value ->
                            value.isRepair = value.id == currentRepairGood!!.id
                        }
                        Log.e("json--", Gson().toJson(it))
                        checkAdapter.setList(it.goods.filter { value -> value.receive_quantity.toFloat() == 0f || value.repair_receive == "1" })
                        if (checkAdapter.data.size > 0) {
                            handlerSelected()
                        }
                    } else {
                        checkAdapter.setList(it.goods.filter { value -> value.receive_quantity.toFloat() == 0f || value.repair_receive == "1" })
                        if (checkAdapter.data.size > 0) {
                            currentGoodsInfo = checkAdapter.data[0]
                            handlerSelected()
                            checkPosition(currentGoodsInfo!!)
                        }
                    }
                }
            }
        })
    }

    private fun checkPosition(item: GoodsInfo) {
        for (i in 0 until checkAdapter.data.size) {
            val goodsInfo = checkAdapter.data[i]
            goodsInfo.isRepair = goodsInfo == item
            checkAdapter.notifyItemChanged(i)
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
                ScaleModule.Instance(this@CheckActivity).RawValue - ScaleModule.Instance(
                    this@CheckActivity
                ).TareWeight,
                ScaleModule.Instance(this@CheckActivity).SetDotPoint
            )
            Log.e("currentWeight", currentWeight)
            if (currentDeliveryType == 1) {
                mBinding.tvDeliveryCurrent.text = formatUnit(currentWeight)
                mBinding.tvCurrentWeight.text = formatUnit(currentWeight)
            } else {
                mBinding.tvCurrentWeight.text = "0.00"
                mBinding.tvDeliveryCurrent.text = "0.00"
            }
        } catch (ee: java.lang.Exception) {
            ee.printStackTrace()
        }
    }

    private fun addUnit(units: MutableList<String>) {
        mBinding.rgUnit.removeAllViews()
        for (index in 0 until units.size) {
            val item = units[index]
            val radioButton = RadioButton(this@CheckActivity)
            radioButton.textSize = sp(18).toFloat()
            radioButton.text = item
            radioButton.id = index
            if (item == viewModel.currentUnit.get()) {
                radioButton.isChecked = true
            }
            if (index != 0) {
                val params = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                params.marginStart = sp(5)
                radioButton.layoutParams = params
            }
            mBinding.rgUnit.addView(radioButton)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        immersionBar {
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            init()
        }
    }

    override fun netStatus(status: Int) {
        super.netStatus(status)
        if (status == 0) {
            show(this@CheckActivity, 2000, "网络已断开连接")
        }
    }

}