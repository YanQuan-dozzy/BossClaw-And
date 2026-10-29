package com.bossclaw.android.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bossclaw.android.R
import com.bossclaw.android.agent.AgentBus
import com.bossclaw.android.agent.BossClawOrchestrator
import com.bossclaw.android.agent.CommunicatorAgent
import com.bossclaw.android.agent.EvaluatorAgent
import com.bossclaw.android.agent.ScoutAgent
import com.bossclaw.android.agent.SupervisorAgent
import com.bossclaw.android.accessibility.AutomationGateway
import com.bossclaw.android.data.db.BossClawDatabase
import com.bossclaw.android.data.pref.SecureStore
import com.bossclaw.android.data.pref.SettingsStore
import com.bossclaw.android.data.repo.BossClawRepository
import com.bossclaw.android.data.repo.ConfigRepository
import com.bossclaw.android.data.repo.SkillRepository
import com.bossclaw.android.domain.JobFilter
import com.bossclaw.android.domain.JobScorer
import com.bossclaw.android.llm.LlmGateway
import com.bossclaw.android.llm.OpenAiCompatibleTransport
import com.bossclaw.android.model.LogLine
import com.bossclaw.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGraph(val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = BossClawDatabase.get(context)
    val repository = BossClawRepository(database)
    val secureStore = SecureStore(context)
    val settingsStore = SettingsStore(context)
    val configRepository = ConfigRepository(secureStore, settingsStore)
    val skillRepository = SkillRepository(context, database.skillDao())
    val transport = OpenAiCompatibleTransport()
    val llmGateway = LlmGateway(transport, repository, configRepository, skillRepository)
    val automator = AutomationGateway(context)
    val jobFilter = JobFilter(repository)
    val scorer = JobScorer()
    val scout = ScoutAgent(automator)
    val evaluator = EvaluatorAgent(repository, configRepository, jobFilter, scorer, llmGateway)
    val communicator = CommunicatorAgent(repository, configRepository, llmGateway, automator)
    val orchestrator = BossClawOrchestrator(repository, configRepository, scout, evaluator, communicator, automator)
    val supervisor = SupervisorAgent(appScope) { reason -> orchestrator.emergencyStop(reason) }

    fun init() {
        createChannel()
        supervisor.start()
        appScope.launch { runCatching { skillRepository.ensureSeeded() } }
    }

    fun log(source: String, message: String, level: String = LogLine.LEVEL_INFO, jobId: String = "") {
        AgentBus.log(source, message, level)
        appScope.launch { repository.recordLog(source, message, level, jobId) }
    }

    fun notify(title: String, body: String) {
        if (!settingsStore.settings.value.notifications) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bossclaw)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(nextNotificationId(), notification) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "BossClaw 状态提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "投递结果、HR 邀约与风控提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun nextNotificationId(): Int = (System.currentTimeMillis() % 100000).toInt()

    companion object {
        const val CHANNEL_ID = "bossclaw_status"
    }
}
