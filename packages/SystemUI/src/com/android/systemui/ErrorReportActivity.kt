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
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.StringBuilderPrinter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ErrorReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title: String
        val reportText: String
        try {
            val intent = getIntent()
            val report = intent.getParcelableExtra(Intent.EXTRA_BUG_REPORT, ApplicationErrorReport::class.java)!!
            val pm = packageManager
            val ai = pm.getApplicationInfo(report.packageName, PackageManager.ApplicationInfoFlags.of(0L))
            title = getString(R.string.error_report_title, ai.loadLabel(pm))
            val headerExt = intent.getStringExtra(Intent.EXTRA_TEXT)
            reportText = errorReportToText(report, headerExt)
        } catch (e: Exception) {
            e.printStackTrace()
            finishAndRemoveTask()
            return
        }

        setTitle(title)

        val textView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            text = reportText
            textSize = 12f
            setTextIsSelectable(true)
            // default color is too light
            val color = if (resources.configuration.isNightModeActive) 0xff_d0_d0_d0 else 0xff_00_00_00
            setTextColor(color.toInt())
        }

        val scroller = ScrollView(this).apply {
            isScrollbarFadingEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            addView(textView)
        }

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

        val btnShare = Button(this).apply {
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

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(btnCopy)
            addView(btnShare)
        }

        val pad = px(16)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttonLayout)
            setPadding(pad, pad, pad, pad)
        }

        setContentView(layout)
    }

    fun px(dp: Int) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX, dp.toFloat(), resources.displayMetrics).toInt()

    fun errorReportToText(r: ApplicationErrorReport, headerExt: String?) =

"""type: ${reportTypeToString(r.type)}
osVersion: ${Build.FINGERPRINT}
package: ${r.packageName}:${r.packageVersion}
process: ${r.processName}${
    if (r.type == ApplicationErrorReport.TYPE_CRASH)
        "\nprocessUptime: ${r.crashInfo.processUptimeMs} + ${r.crashInfo.processStartupLatencyMs} ms"
    else
        ""
}${if (headerExt != null) "\n$headerExt" else ""}

${reportInfoToString(r)}"""

    fun reportInfoToString(r: ApplicationErrorReport): String {
        if (r.type == ApplicationErrorReport.TYPE_CRASH) {
            return r.crashInfo.stackTrace
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
