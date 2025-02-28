package com.didichuxing.doraemonkit.kit.core

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.didichuxing.doraemonkit.DoKit
import com.didichuxing.doraemonkit.R
import com.didichuxing.doraemonkit.kit.core.DoKitManager.WS_MODE
import com.didichuxing.doraemonkit.constant.WSMode
import com.didichuxing.doraemonkit.extension.tagName
import com.didichuxing.doraemonkit.kit.main.MainIconDoKitView
import com.didichuxing.doraemonkit.kit.performance.PerformanceDokitView
import com.didichuxing.doraemonkit.kit.toolpanel.ToolPanelDoKitView
import com.didichuxing.doraemonkit.util.*
import java.util.*

/**
 * Created by jintai on 2018/10/23.
 * 每个activity悬浮窗管理类
 */
internal class NormalDoKitViewManager : AbsDokitViewManager() {
    companion object {
        private const val MC_DELAY = 100
    }

    /**
     * 每个Activity中dokitView的集合 用户手动移除和页面销毁时都需要remove
     *
     */
    private val mActivityDoKitViewMap: MutableMap<Activity, MutableMap<String, AbsDokitView>> by lazy {
        mutableMapOf<Activity, MutableMap<String, AbsDokitView>>()
    }

    /**
     * 只用来记录全局的同步  只有用户手动移除时才会remove
     */
    private val mGlobalSingleDoKitViewInfoMap: MutableMap<String, GlobalSingleDokitViewInfo> by lazy {
        mutableMapOf<String, GlobalSingleDokitViewInfo>()
    }

    private val mContext: Context by lazy { DoKit.APPLICATION }


    /**
     * 当app进入后台时调用
     */
    override fun notifyBackground() {
        mActivityDoKitViewMap.forEach { maps ->
            maps.value.forEach { map ->
                map.value.onEnterBackground()
            }
        }
    }

    /**
     * 当app进入前台时调用
     */
    override fun notifyForeground() {
        mActivityDoKitViewMap.forEach { maps ->
            maps.value.forEach { map ->
                map.value.onEnterForeground()
            }
        }
    }

    /**
     * 添加activity关联的所有dokitView activity resume的时候回调
     *
     * @param activity
     */
    override fun dispatchOnActivityResumed(activity: Activity?) {
        if (activity == null) {
            return
        }

        //app启动
        if (DoKitSystemUtil.isOnlyFirstLaunchActivity(activity)) {
            onMainActivityResume(activity)
            return
        }
        DoKitManager.ACTIVITY_LIFECYCLE_INFOS[activity.javaClass.canonicalName]?.let {
            //新建Activity
            if (it.lifeCycleStatus == DoKitLifeCycleStatus.RESUME && it.isInvokeStopMethod == false) {
                onActivityResume(activity)
            }

            //activity resume
            if (it.lifeCycleStatus == DoKitLifeCycleStatus.RESUME && it.isInvokeStopMethod == true) {
                onActivityBackResume(activity)
            }
        }

    }


    override fun attachMainIcon(activity: Activity?) {
        if (activity == null) {
            return
        }

        //假如不存在全局的icon这需要全局显示主icon
        if (DoKitManager.ALWAYS_SHOW_MAIN_ICON && activity !is UniversalActivity) {
            attach(DokitIntent(MainIconDoKitView::class.java))
            DoKitManager.MAIN_ICON_HAS_SHOW = true
        } else {
            DoKitManager.MAIN_ICON_HAS_SHOW = false
        }
    }

    override fun detachMainIcon() {
        detach(MainIconDoKitView::class.tagName)
    }

    override fun attachToolPanel(activity: Activity?) {
        attach(DokitIntent(ToolPanelDoKitView::class.java))
    }

    override fun detachToolPanel() {
        detach(ToolPanelDoKitView::class.tagName)
    }

    /**
     * 应用启动
     */
    override fun onMainActivityResume(activity: Activity?) {
        if (activity == null) {
            return
        }

        if (activity is UniversalActivity) {
            return
        }
        //主icon
        attachMainIcon(activity)
        //倒计时DokitView
        attachCountDownDoKitView(activity)
        //启动一机多控悬浮窗
        attachMcRecodingDoKitView(activity)
    }


    /**
     * 新建activity
     *
     * @param activity
     */
    override fun onActivityResume(activity: Activity?) {
        if (activity == null) {
            return
        }

        //将所有的dokitView添加到新建的Activity中去
        for (dokitViewInfo: GlobalSingleDokitViewInfo in mGlobalSingleDoKitViewInfoMap.values) {
            //如果不是性能kitView 则不显示
            if (activity is UniversalActivity && dokitViewInfo.absDokitViewClass != PerformanceDokitView::class.java) {
                continue
            }
            //是否过滤掉 入口icon
            if (!DoKitManager.ALWAYS_SHOW_MAIN_ICON && dokitViewInfo.absDokitViewClass == MainIconDoKitView::class.java) {
                DoKitManager.MAIN_ICON_HAS_SHOW = false
                continue
            }

            if (dokitViewInfo.absDokitViewClass == MainIconDoKitView::class.java) {
                DoKitManager.MAIN_ICON_HAS_SHOW = true
            }

            val dokitIntent = DokitIntent(dokitViewInfo.absDokitViewClass)
            dokitIntent.bundle = dokitViewInfo.bundle
            dokitIntent.mode = dokitViewInfo.mode
            dokitIntent.tag = dokitViewInfo.tag
            attach(dokitIntent)
        }

        attachMainIcon(activity)
        //开始之前先移除
        detachCountDownDoKitView(activity)
        //倒计时DokitView
        attachCountDownDoKitView(activity)
        //启动一机多控悬浮窗
        attachMcRecodingDoKitView(activity)
    }

    /**
     * activity onResume
     *
     * @param activity
     */
    override fun onActivityBackResume(activity: Activity?) {
        if (activity == null) {
            return
        }

        val existDoKitViews: Map<String, AbsDokitView>? = mActivityDoKitViewMap[activity]
        //更新所有全局DokitView的位置
        if (mGlobalSingleDoKitViewInfoMap.isNotEmpty()) {
            for (gDoKitViewInfo in mGlobalSingleDoKitViewInfoMap.values) {
                //如果不是性能kitView 则需要重新更新位置
                if (activity is UniversalActivity && gDoKitViewInfo.absDokitViewClass != PerformanceDokitView::class.java) {
                    continue
                }
                //是否过滤掉 入口icon
                if (!DoKitManager.ALWAYS_SHOW_MAIN_ICON && gDoKitViewInfo.absDokitViewClass == MainIconDoKitView::class.java) {
                    DoKitManager.MAIN_ICON_HAS_SHOW = false
                    continue
                }
                if (gDoKitViewInfo.absDokitViewClass == MainIconDoKitView::class.java) {
                    DoKitManager.MAIN_ICON_HAS_SHOW = true
                }

                //判断resume Activity 中时候存在指定的dokitview
                var existDoKitView: AbsDokitView? = null
                if (existDoKitViews != null && existDoKitViews.isNotEmpty()) {
                    existDoKitView = existDoKitViews[gDoKitViewInfo.tag]
                }

                //当前页面已存在dokitview
                if (existDoKitView?.doKitView != null) {
                    existDoKitView.doKitView?.visibility = View.VISIBLE
                    //更新位置
                    existDoKitView.updateViewLayout(existDoKitView.tag, true)
                    existDoKitView.onResume()
                } else {
                    //添加相应的
                    val doKitIntent = DokitIntent(gDoKitViewInfo.absDokitViewClass)
                    doKitIntent.mode = gDoKitViewInfo.mode
                    doKitIntent.bundle = gDoKitViewInfo.bundle
                    doKitIntent.tag = gDoKitViewInfo.tag
                    attach(doKitIntent)
                }
            }
        }
        //假如不存在全局的icon这需要全局显示主icon
        attachMainIcon(activity)
        //开始之前先移除
        detachCountDownDoKitView(activity)
        attachCountDownDoKitView(activity)
        //启动一机多控悬浮窗
        attachMcRecodingDoKitView(activity)
    }


    override fun onActivityPaused(activity: Activity?) {
        if (activity == null) {
            return
        }

        val doKitViews = getDoKitViews(activity)
        doKitViews.let {
            for (doKitView: AbsDokitView in it.values) {
                doKitView.onPause()
            }
        }

    }

    private fun detachCountDownDoKitView(activity: Activity) {
        val countDownDoKitView = mutableListOf<AbsDokitView>()
        mActivityDoKitViewMap[activity]?.forEach {
            if (it.value.mode == DoKitViewLaunchMode.COUNTDOWN) {
                countDownDoKitView.add(it.value)
            }
        }
        countDownDoKitView.forEach {
            detach(it.tag)
        }
    }

    override fun onActivityStopped(activity: Activity?) {

    }

    /**
     * 在当前Activity中添加指定悬浮窗
     *
     * @param doKitIntent
     */
    override fun attach(doKitIntent: DokitIntent) {
        try {
            //判断当前Activity是否存在dokitView map
            val currentActivityDoKitViews: MutableMap<String, AbsDokitView> = when {
                (mActivityDoKitViewMap[doKitIntent.activity] == null) -> {
                    val doKitViewMap = mutableMapOf<String, AbsDokitView>()
                    mActivityDoKitViewMap[doKitIntent.activity] = doKitViewMap
                    doKitViewMap
                }
                else -> {
                    mActivityDoKitViewMap[doKitIntent.activity]!!
                }
            }

            //判断该dokitview是否已经显示在页面上 同一个类型的dokitview 在页面上只显示一个
            if (currentActivityDoKitViews[doKitIntent.tag] != null) {
                //拿到指定的dokitView并更新位置
                currentActivityDoKitViews[doKitIntent.tag]?.updateViewLayout(
                    doKitIntent.tag,
                    true
                )
                return
            }
            val doKitView = doKitIntent.targetClass.newInstance()
            //在当前Activity中保存dokitView
            //设置dokitview的属性
            doKitView.mode = doKitIntent.mode
            doKitView.bundle = doKitIntent.bundle
            doKitView.tag = doKitIntent.tag
            doKitView.setActivity(doKitIntent.activity)
            doKitView.performCreate(mContext)
            //在全局dokitviews中保存该类型的

            mGlobalSingleDoKitViewInfoMap[doKitView.tag] =
                createGlobalSingleDokitViewInfo(doKitView)

            //得到activity window中的根布局
            //final ViewGroup mDecorView = getDecorView(dokitIntent.activity);

            //往DecorView的子RootView中添加dokitView
            if (doKitView.normalLayoutParams != null && doKitView.doKitView != null) {
                getDoKitRootContentView(doKitIntent.activity)
                    .addView(
                        doKitView.doKitView,
                        doKitView.normalLayoutParams
                    )
                //延迟100毫秒调用
                doKitView.postDelayed(Runnable {
                    doKitView.onResume()
                    //操作DecorRootView
                    doKitView.dealDecorRootView(getDoKitRootContentView(doKitIntent.activity))
                }, MC_DELAY.toLong())

            }
            currentActivityDoKitViews[doKitView.tag] = doKitView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    //    private static final String DOKIT_ROOT_VIEW_TAG = "DokitRootView";
    /**
     * @return rootView
     */
    private fun getDoKitRootContentView(activity: Activity): FrameLayout {
        val decorView = getDecorView(activity)
        var doKitRootView = decorView.findViewById<FrameLayout>(R.id.dokit_contentview_id)
        if (doKitRootView != null) {
            return doKitRootView
        }
        doKitRootView = DokitFrameLayout(mContext, DokitFrameLayout.DoKitFrameLayoutFlag_ROOT)
        //普通模式的返回按键监听
        doKitRootView.setOnKeyListener(View.OnKeyListener { _, keyCode, _ ->
            //监听返回键
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                val doKitViewMap: Map<String, AbsDokitView>? = getDoKitViews(activity)
                if (doKitViewMap == null || doKitViewMap.isEmpty()) {
                    return@OnKeyListener false
                }
                for (doKitView in doKitViewMap.values) {
                    if (doKitView.shouldDealBackKey()) {
                        return@OnKeyListener doKitView.onBackPressed()
                    }
                }
                return@OnKeyListener false
            }
            false
        })
        doKitRootView.setClipChildren(false)
        doKitRootView.setClipToPadding(false)

        //解决无法获取返回按键的问题
        doKitRootView.setFocusable(true)
        doKitRootView.setFocusableInTouchMode(true)
        doKitRootView.requestFocus()
        doKitRootView.setId(R.id.dokit_contentview_id)
        val doKitParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        try {
            //解决由于项目集成SwipeBackLayout而出现的dokit入口不显示
            if (BarUtils.isStatusBarVisible((activity))) {
                doKitParams.topMargin = BarUtils.getStatusBarHeight()
            }
            if (BarUtils.isSupportNavBar()) {
                if (BarUtils.isNavBarVisible((activity))) {
                    doKitParams.bottomMargin = BarUtils.getNavBarHeight()
                }
            }
        } catch (e: Exception) {
            //e.printStackTrace();
        }
        doKitParams.gravity = Gravity.BOTTOM
        doKitRootView.setLayoutParams(doKitParams)
        //添加到DecorView中 为了不和用户自己往根布局中添加view干扰
        decorView.addView(doKitRootView)
        return doKitRootView
    }

    /**
     * 移除每个activity指定的dokitView
     */
    override fun detach(dokitView: AbsDokitView) {
        //调用当前Activity的指定dokitView的Destroy方法
        //dokitView.performDestroy();
        detach(dokitView.tag)
    }


    /**
     * 根据tag 移除ui和列表中的数据
     *
     * @param tag
     */
    override fun detach(tag: String) {
        realDetach(tag)
    }

    private fun realDetach(tag: String) {
        //移除每个activity中指定的dokitView
        for (activityKey in mActivityDoKitViewMap.keys) {
            val doKitViewMap = mActivityDoKitViewMap[activityKey]
            //定位到指定dokitView
            val doKitView = doKitViewMap?.get(tag) ?: continue
            if (doKitView.doKitView != null) {
                doKitView.doKitView?.visibility = View.GONE
                getDoKitRootContentView(doKitView.activity).removeView(doKitView.doKitView)
            }

            //移除指定UI
            //请求重新绘制
            getDecorView(activityKey).requestLayout()
            //执行dokitView的销毁
            doKitView.performDestroy()
            //移除map中的数据
            doKitViewMap.remove(tag)
        }
        //同步移除全局指定类型的dokitView
        if (mGlobalSingleDoKitViewInfoMap.containsKey(tag)) {
            mGlobalSingleDoKitViewInfoMap.remove(tag)
        }
    }


    override fun detach(doKitViewClass: Class<out AbsDokitView>) {
        detach(doKitViewClass.tagName)
    }


    /**
     * 移除所有activity的所有dokitView
     */
    override fun detachAll() {

        //移除每个activity中所有的dokitView
        for (activityKey: Activity in mActivityDoKitViewMap.keys) {
            val doKitViewMap = mActivityDoKitViewMap[activityKey]
            //移除指定UI
            getDoKitRootContentView(activityKey).removeAllViews()
            //移除map中的数据
            doKitViewMap?.clear()
        }
        mGlobalSingleDoKitViewInfoMap.clear()
    }

    /**
     * 获取当前页面指定的dokitView
     *
     * @param activity
     * @param tag
     * @return AbsDokitView
     */
    override fun <T : AbsDokitView> getDoKitView(
        activity: Activity?,
        clazz: Class<T>
    ): AbsDokitView? {
        if (TextUtils.isEmpty(clazz.tagName)) {
            return null
        }

        return if (mActivityDoKitViewMap[activity] == null) {
            null
        } else mActivityDoKitViewMap[activity]?.get(clazz.tagName)
    }

    /**
     * Activity销毁时调用
     */
    override fun onActivityDestroyed(activity: Activity?) {
        if (activity == null) {
            return
        }

        //移除dokit根布局
        val doKitRootView = activity.findViewById<View>(R.id.dokit_contentview_id)
        if (doKitRootView != null) {
            getDecorView(activity).removeView(doKitRootView)
        }
        val doKitViewMap: Map<String, AbsDokitView> = getDoKitViews(activity)
        for (doKitView in doKitViewMap.values) {
            doKitView.performDestroy()
        }
        mActivityDoKitViewMap.remove(activity)


    }

    /**
     * 获取页面根布局
     *
     * @param activity
     * @return
     */
    private fun getDecorView(activity: Activity): ViewGroup {
        return activity.window.decorView as ViewGroup
    }


    /**
     * 获取当前页面所有的dokitView
     *
     * @param activity
     * @return
     */
    override fun getDoKitViews(activity: Activity?): Map<String, AbsDokitView> {
        return mActivityDoKitViewMap[activity] ?: emptyMap()
    }

    private fun createGlobalSingleDokitViewInfo(dokitView: AbsDokitView): GlobalSingleDokitViewInfo {
        return GlobalSingleDokitViewInfo(
            dokitView.javaClass,
            dokitView.tag,
            dokitView.mode,
            dokitView.bundle
        )
    }


}