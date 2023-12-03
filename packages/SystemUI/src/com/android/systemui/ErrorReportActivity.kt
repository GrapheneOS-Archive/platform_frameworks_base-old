/*
 * Copyright (C) 2022 GrapheneOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui

import android.app.Activity
import android.app.ApplicationErrorReport
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.ext.ErrorReportUi
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.StringBuilderPrinter
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

class ErrorReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = this

        val title: CharSequence
        val reportText: String
        val showReportButton: Boolean
        try {
            val intent = getIntent()
            when (intent.action) {
                ErrorReportUi.ACTION_CUSTOM_REPORT -> {
                    val type = intent.getStringExtra(ErrorReportUi.EXTRA_TYPE) ?: "crash"
                    var prefix = "type: $type\n"
                    val msgCompressed = intent.getByteArrayExtra(ErrorReportUi.EXTRA_GZIPPED_MESSAGE)!!

                    val msgBytes = GZIPInputStream(ByteArrayInputStream(msgCompressed)).use {
                        it.readAllBytes()
                    }

                    val msg = String(msgBytes, StandardCharsets.UTF_8)

                    if (!msg.contains(Build.FINGERPRINT)) {
                        prefix += "osVersion: ${Build.FINGERPRINT}\n"
                    }

                    title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: run {
                        val sourcePkg = intent.getStringExtra(ErrorReportUi.EXTRA_SOURCE_PACKAGE)
                        return@run if (sourcePkg == null) {
                            ""
                        } else {
                            loadTitle(sourcePkg)
                        }
                    }

                    reportText = prefix + msg;
                }
                Intent.ACTION_APP_ERROR -> {
                    val report = intent.getParcelableExtra(Intent.EXTRA_BUG_REPORT, ApplicationErrorReport::class.java)!!
                    title = loadTitle(report.packageName)
                    val headerExt = intent.getStringExtra(Intent.EXTRA_TEXT)
                    reportText = errorReportToText(report, headerExt)
                }
                else ->
                    throw IllegalArgumentException(intent.toString())
            }
            showReportButton = intent.getBooleanExtra(ErrorReportUi.EXTRA_SHOW_REPORT_BUTTON, false)
        } catch (e: Exception) {
            e.printStackTrace()
            finishAndRemoveTask()
            return
        }

        setTitle(title)

        val formattedReportText = "```\n" + reportText + "\n```"
        val clipData = ClipData.newPlainText(title, formattedReportText)

        val btnCopy = Button(this).apply {
            setText(R.string.copy_to_clipboard)
            setOnClickListener { _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(clipData)
                Toast.makeText(this@ErrorReportActivity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btnCopy)
            if (showReportButton) {
                val btnReport = Button(ctx).apply {
                    setText(R.string.report_error_btn)
                    setOnClickListener { _ ->
                        val cm = getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(clipData)
                        val url = "https://github.com/GrapheneOS/os-issue-tracker/issues"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                addView(btnReport)
            } else {
                val btnShare = Button(ctx).apply {
                    setText(R.string.error_share)
                    setOnClickListener { _ ->
                        val i = Intent(Intent.ACTION_SEND)
                        i.clipData = clipData
                        i.type = ClipDescription.MIMETYPE_TEXT_PLAIN
                        i.putExtra(Intent.EXTRA_SUBJECT, title)
                        i.putExtra(Intent.EXTRA_TEXT, formattedReportText)
                        startActivity(Intent.createChooser(i, title))
                    }
                }
                addView(btnShare)
            }
        }

        val list = reportText.split("\n")

        class VHolder(val text: TextView) : RecyclerView.ViewHolder(text)

        val listAdapter = object : RecyclerView.Adapter<VHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VHolder {
                val v = TextView(ctx).apply {
                    typeface = Typeface.MONOSPACE
                    text = reportText
                    textSize = 12f
                    // default color is too light
                    val color = if (resources.configuration.isNightModeActive) 0xff_d0_d0_d0 else 0xff_00_00_00
                    setTextColor(color.toInt())
                }
                return VHolder(v)
            }

            override fun onBindViewHolder(holder: VHolder, position: Int) {
                holder.text.setText(list[position])
            }

            override fun getItemCount() = list.size
        }

        val listView = RecyclerView(this).apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(ctx)
            // needed for state restoration
            id = 1
        }

        val pad = px(16)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttonLayout)
            setPadding(pad, pad, pad, pad)
        }

        setContentView(layout)
    }

    fun loadTitle(pkgName: String): CharSequence {
        val pm = packageManager

        val ai = try {
            pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0L))
        } catch (e: NameNotFoundException) {
            return pkgName
        }

        return getString(R.string.error_report_title, ai.loadLabel(pm))
    }

    fun px(dp: Int) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX, dp.toFloat(), resources.displayMetrics).toInt()

    fun errorReportToText(r: ApplicationErrorReport, headerExt: String?): String {
        fun line(s: String?) = if (s != null) "\n$s" else ""

        return """type: ${reportTypeToString(r.type)}
osVersion: ${Build.FINGERPRINT}
package: ${r.packageName}:${r.packageVersion}
process: ${r.processName}${
    if (r.type == ApplicationErrorReport.TYPE_CRASH)
        "\nprocessUptime: ${r.crashInfo.processUptimeMs} + ${r.crashInfo.processStartupLatencyMs} ms"
    else
        ""
}${
    line(installerInfo(r.packageName))}${
    line(headerExt)
}

${reportInfoToString(r)}"""
    }

    fun installerInfo(pkgName: String): String? {
        try {
            val isi = packageManager.getInstallSourceInfo(pkgName)
            isi.installingPackageName?.let {
                return "installer: $it"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    fun reportInfoToString(r: ApplicationErrorReport): String {
        if (r.type == ApplicationErrorReport.TYPE_CRASH) {
            val stackTrace = r.crashInfo.stackTrace
            val nativeCrashMarkerIdx = stackTrace.indexOf("\nProcess uptime: ")
            if (nativeCrashMarkerIdx > 0) {
                // This is a native crash, filter out most of the header lines to make the report easier to read
                val sb = StringBuilder()
                val prefixes = arrayOf("signal ", "Abort message: ")
                var backtraceStarted = false
                for (line in stackTrace.substring(nativeCrashMarkerIdx).lines()) {
                    if (backtraceStarted) {
                        sb.append(line)
                        sb.append('\n')
                    }
                    for (prefix in prefixes) {
                        if (line.startsWith(prefix)) {
                            sb.append(line)
                            sb.append('\n')
                        }
                    }
                    if (line.startsWith("backtrace:")) {
                        sb.append('\n')
                        sb.append(line)
                        sb.append('\n')
                        backtraceStarted = true
                    }
                }
                return sb.toString()
            } else {
                return stackTrace
            }
        }

        val sb = StringBuilder()
        val printer = StringBuilderPrinter(sb)

        when (r.type) {
            ApplicationErrorReport.TYPE_ANR ->
                r.anrInfo.dump(printer, "")
            ApplicationErrorReport.TYPE_BATTERY ->
                r.batteryInfo.dump(printer, "")
            ApplicationErrorReport.TYPE_RUNNING_SERVICE ->
                r.runningServiceInfo.dump(printer, "")
        }

        return sb.toString()
    }

    fun reportTypeToString(type: Int) = when (type) {
        ApplicationErrorReport.TYPE_CRASH -> "crash"
        ApplicationErrorReport.TYPE_ANR -> "ANR"
        ApplicationErrorReport.TYPE_BATTERY -> "battery"
        ApplicationErrorReport.TYPE_RUNNING_SERVICE -> "running_service"
        else -> "unknown ($type)"
    }
}
