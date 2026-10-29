package com.bossclaw.android.accessibility.model

import java.security.MessageDigest

data class ScrapedRawJob(
    val jobId: String = "",
    val title: String = "",
    val companyName: String = "",
    val salaryText: String = "",
    val city: String = "",
    val experience: String = "",
    val degree: String = "",
    val hrName: String = "",
    val hrTitle: String = "",
    val hrActiveStatus: String = "",
    val jobDescription: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun resolveJobId(): String {
        if (jobId.isNotBlank()) return jobId
        return fingerprint(title, companyName, salaryText)
    }

    fun isValid(): Boolean = title.isNotBlank() && companyName.isNotBlank()

    companion object {
        fun fingerprint(title: String, company: String, salary: String): String {
            val raw = "${title.trim().lowercase()}|${company.trim().lowercase()}|${salary.trim().lowercase()}"
            val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
