package com.bossclaw.android.accessibility.task

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.model.ScrapedRawJob
import com.bossclaw.android.accessibility.util.GestureEngine

class InspectDetailTask(
    private val cardClickBounds: Rect? = null,
    private val onJobScraped: ((ScrapedRawJob) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    companion object {
        private const val STEP_CLICK_CARD = 1
        private const val STEP_VERIFY_DETAIL_PAGE = 2
        private const val STEP_EXTRACT_DATA = 3

        private const val ID_JOB_NAME = "com.hpbr.bosszhipin:id/tv_job_name"
        private const val ID_JOB_SALARY = "com.hpbr.bosszhipin:id/tv_job_salary"
        private const val ID_LOCATION = "com.hpbr.bosszhipin:id/tv_required_location"
        private const val ID_BOSS_NAME = "com.hpbr.bosszhipin:id/tv_boss_name"
        private const val ID_BOSS_TITLE = "com.hpbr.bosszhipin:id/tv_boss_title"
        private const val ID_BOSS_LABEL = "com.hpbr.bosszhipin:id/boss_label_tv"
        private const val ID_DESCRIPTION = "com.hpbr.bosszhipin:id/tv_description"
        private const val ID_COMPANY_NAME = "com.hpbr.bosszhipin:id/tv_com_name"
        private const val ID_CHAT_BUTTON = "com.hpbr.bosszhipin:id/btn_chat"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_CLICK_CARD -> handleClickCard()
            STEP_VERIFY_DETAIL_PAGE -> handleVerifyDetailPage()
            STEP_EXTRACT_DATA -> handleExtractData()
        }
    }

    private fun handleClickCard() {
        val bounds = cardClickBounds
        if (bounds == null) {
            sendStep(STEP_VERIFY_DETAIL_PAGE, 100L)
            return
        }
        sendTaskMessage("打开目标岗位详情")
        val clicked = GestureEngine.clickAt(
            service,
            bounds.centerX(),
            bounds.centerY(),
            onSuccess = { nextStep(900L) },
            onFail = { retryCurrentStep(400L, "点击卡片失败") }
        )
        if (!clicked) retryCurrentStep(500L, "无法派发卡片点击")
    }

    private fun handleVerifyDetailPage() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待详情页渲染")
            return
        }
        val hasChatBtn = root.findAccessibilityNodeInfosByViewId(ID_CHAT_BUTTON).isNotEmpty()
        val hasJobName = root.findAccessibilityNodeInfosByViewId(ID_JOB_NAME).isNotEmpty()
        root.recycle()
        if (hasChatBtn || hasJobName) nextStep(200L)
        else retryCurrentStep(500L, "尚未检测到详情页特征元素")
    }

    private fun handleExtractData() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "提取数据时根节点为空")
            return
        }
        val title = safeExtractText(root, ID_JOB_NAME)
        val salary = safeExtractText(root, ID_JOB_SALARY)
        val location = safeExtractText(root, ID_LOCATION)
        val hrName = safeExtractText(root, ID_BOSS_NAME)
        val hrTitle = safeExtractText(root, ID_BOSS_TITLE)
        val hrActive = safeExtractText(root, ID_BOSS_LABEL)
        val description = safeExtractText(root, ID_DESCRIPTION)
        val company = safeExtractText(root, ID_COMPANY_NAME)
        root.recycle()

        if (title.isBlank() && description.isBlank()) {
            retryCurrentStep(500L, "岗位名与 JD 均为空")
            return
        }

        val experience = Regex("""\d{1,2}\s*[-~]\s*\d{1,2}\s*年|经验不限|应届|在校""")
            .find("$location ${description.take(200)}")?.value.orEmpty()
        val degree = Regex("""本科|大专|硕士|博士|学历不限|中专|高中""").find("$location ${description.take(200)}")?.value.orEmpty()

        val job = ScrapedRawJob(
            title = title,
            companyName = company,
            salaryText = salary,
            city = location,
            experience = experience,
            degree = degree,
            hrName = hrName,
            hrTitle = hrTitle,
            hrActiveStatus = hrActive,
            jobDescription = description,
            timestamp = System.currentTimeMillis()
        )
        if (!job.isValid()) {
            failedTask("采集信息不完整：${job.title} / ${job.companyName}")
            return
        }
        sendTaskMessage("已采集【${job.companyName} · ${job.title}】，JD ${description.length} 字")
        onJobScraped?.invoke(job)
        finishTask()
    }

    private fun safeExtractText(root: AccessibilityNodeInfo, viewId: String): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return ""
        val text = nodes.firstOrNull()?.text?.toString()?.trim() ?: ""
        nodes.forEach { it.recycle() }
        return text
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onError?.invoke(reason)
    }

    override fun onForceStopped() {
        onError?.invoke("岗位详情任务被强制终止")
    }
}
