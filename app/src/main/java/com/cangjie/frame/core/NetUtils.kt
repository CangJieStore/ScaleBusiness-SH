package com.cangjie.frame.core

import android.content.Context
import android.net.ConnectivityManager

/**
 * @author: guruohan
 * @date: 2021/11/6
 */
class NetUtils {
    companion object {
        //判断网络状态，有网络返回true
        fun isConnected(context: Context?): Boolean {
            if (isNetworkConnected(context) || isWifiConnected(context)) {
                return true
            }
            return false
        }

        //判断手机是否有网络连接
        fun isNetworkConnected(context: Context?): Boolean {
            if (context != null) {
                val mConnectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val mNetworkInfo = mConnectivityManager.activeNetworkInfo
                if (mNetworkInfo != null) {
                    return mNetworkInfo.isAvailable
                }
            }
            return false
        }

        //判断wifi网络是否可用
        fun isWifiConnected(context: Context?): Boolean {
            if (context != null) {
                val mConnectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val mNetworkInfo =
                    mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                if (mNetworkInfo != null) {
                    return mNetworkInfo.isAvailable
                }
            }
            return false
        }
    }
}