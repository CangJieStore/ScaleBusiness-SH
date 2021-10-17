package cangjie.scale.business.entity

data class Update(
    val versionCode: Int,
    val versionName: String,
    val forceUpdate: Boolean,
    val updateLog: String,
    val apkUrl: String
)
