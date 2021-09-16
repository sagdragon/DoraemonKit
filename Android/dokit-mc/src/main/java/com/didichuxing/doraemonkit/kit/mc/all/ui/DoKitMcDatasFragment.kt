package com.didichuxing.doraemonkit.kit.mc.all.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.didichuxing.doraemonkit.constant.WSMode
import com.didichuxing.doraemonkit.extension.doKitGlobalScope
import com.didichuxing.doraemonkit.kit.core.DoKitManager
import com.didichuxing.doraemonkit.kit.core.BaseFragment
import com.didichuxing.doraemonkit.kit.mc.ability.McHttpManager
import com.didichuxing.doraemonkit.kit.mc.all.DoKitMcManager
import com.didichuxing.doraemonkit.kit.mc.data.McConfigInfo
import com.didichuxing.doraemonkit.mc.R
import com.didichuxing.doraemonkit.util.SPUtils
import com.didichuxing.doraemonkit.util.ToastUtils
import com.didichuxing.doraemonkit.widget.recyclerview.DividerItemDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/12/10-10:52
 * 描    述：
 * 修订历史：
 * ================================================
 */
class DoKitMcDatasFragment : BaseFragment() {
    private lateinit var mRv: RecyclerView
    private lateinit var mEmpty: TextView
    private lateinit var mAdapter: McCaseListAdapter

    override fun onRequestLayout(): Int {
        return R.layout.dk_fragment_mc_datas
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRv = findViewById(R.id.rv)
        mEmpty = findViewById(R.id.tv_empty)

        mAdapter = McCaseListAdapter(mutableListOf<McCaseInfo>())
        mAdapter.setOnItemClickListener { adapter, _, pos ->
            val item = adapter.data[pos] as McCaseInfo
            if (item.isChecked) {
                for (i in adapter.data) {
                    (i as McCaseInfo).isChecked = false
                }
                saveCaseId("")
            } else {
                for (i in adapter.data) {
                    (i as McCaseInfo).isChecked = false
                }
                item.isChecked = true
                saveCaseId(item.caseId)
                ToastUtils.showShort("用例: ${item.caseName} 已被选中")
            }
            if (DoKitManager.WS_MODE == WSMode.HOST) {
                doKitGlobalScope.launch {
                    delay(100)
                    adapter.notifyDataSetChanged()
                }
            } else {
                adapter.notifyDataSetChanged()
            }

        }
        mRv.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(requireActivity())
            val decoration = DividerItemDecoration(DividerItemDecoration.VERTICAL)
            decoration.setDrawable(resources.getDrawable(R.drawable.dk_divider))
            addItemDecoration(decoration)
        }


        lifecycleScope.launch {
            val data = McHttpManager.caseList<McCaseInfo>().data
            data?.let {
                if (it.isEmpty()) {
                    mEmpty.visibility = View.VISIBLE
                } else {
                    val caseId = loadCaseId()
                    it.forEach { info ->
                        info.isChecked = caseId == info.caseId
                    }
                    mAdapter.setList(it)
                }

            }
        }

    }

    private fun saveCaseId(caseId: String) {
        DoKitMcManager.MC_CASE_ID = caseId
        SPUtils.getInstance().put(DoKitMcManager.MC_CASE_ID_KEY, caseId)
    }


    private fun loadCaseId(): String = when {
        DoKitMcManager.MC_CASE_ID.isEmpty() -> {
            val caseId = SPUtils.getInstance().getString(DoKitMcManager.MC_CASE_ID_KEY, "")
            DoKitMcManager.MC_CASE_ID = caseId
            DoKitMcManager.MC_CASE_ID
        }
        else -> {
            DoKitMcManager.MC_CASE_ID
        }
    }

}