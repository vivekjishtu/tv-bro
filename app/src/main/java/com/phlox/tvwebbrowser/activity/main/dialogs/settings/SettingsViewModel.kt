package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.activity.main.MainActivity
import com.phlox.tvwebbrowser.utils.UpdateChecker
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.sameDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException
import java.util.*

class SettingsViewModel: ViewModel() {
    companion object {
        val TAG = SettingsViewModel::class.java.simpleName
        const val SEARCH_ENGINE_URL_PREF_KEY = "search_engine_url"
        const val USER_AGENT_PREF_KEY = "user_agent"
        const val LAST_UPDATE_USER_NOTIFICATION_TIME_KEY="last_update_notif"
        const val AUTOCHECK_UPDATES_KEY="auto_check_updates"
        const val UPDATE_CHANNEL_KEY="update_channel"
        const val TV_BRO_UA_PREFIX = "TV Bro/1.0 "
    }

    private var prefs: SharedPreferences

    //Search engines configuration
    val SearchEnginesTitles = arrayOf("Google", "Bing", "Yahoo!", "DuckDuckGo", "Yandex", "Custom")
    val SearchEnginesURLs = listOf("https://www.google.com/search?q=[query]", "https://www.bing.com/search?q=[query]",
            "https://search.yahoo.com/search?p=[query]", "https://duckduckgo.com/?q=[query]",
            "https://yandex.com/search/?text=[query]", "")
    var searchEngineURL = MutableLiveData<String>()
    //User agent strings configuration
    val userAgentStringTitles = arrayOf("TV Bro", "Chrome (Desktop)", "Chrome (Mobile)", "Chrome (Tablet)", "Firefox (Desktop)", "Firefox (Tablet)", "Edge (Desktop)", "Safari (Desktop)", "Safari (iPad)", "Apple TV", "Custom")
    val uaStrings = listOf("",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 8.0; Pixel 2 Build/OPD3.170816.012) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:78.0) Gecko/20100101 Firefox/78.0",
            "Mozilla/5.0 (Android 10; Tablet; rv:68.0) Gecko/68.0 Firefox/68.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36 Edg/84.0.522.44",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1 Safari/605.1.15",
            "Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Mobile/15E148 Safari/604.1",
            "AppleTV6,2/11.1",
            "")
    var uaString = MutableLiveData<String>()
    //Version & updates configuration
    var needToShowUpdateDlgAgain: Boolean = false
    val updateChecker = UpdateChecker(BuildConfig.VERSION_CODE)
    var lastUpdateNotificationTime: Calendar
    var needAutockeckUpdates: Boolean
    var updateChannel: String

    init {
        prefs = TVBro.instance.getSharedPreferences(TVBro.MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        searchEngineURL.postValue(prefs.getString(SEARCH_ENGINE_URL_PREF_KEY, ""))
        uaString.postValue(prefs.getString(USER_AGENT_PREF_KEY, ""))
        lastUpdateNotificationTime = if (prefs.contains(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY))
            Calendar.getInstance().apply { timeInMillis = prefs.getLong(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY, 0) } else
            Calendar.getInstance()
        needAutockeckUpdates = prefs.getBoolean(AUTOCHECK_UPDATES_KEY, Utils.isInstalledByAPK(TVBro.instance))
        updateChannel = prefs.getString(UPDATE_CHANNEL_KEY, "release")!!
    }

    fun changeSearchEngineUrl(url: String) {
        val editor = prefs.edit()
        editor.putString(SEARCH_ENGINE_URL_PREF_KEY, url)
        editor.apply()
        searchEngineURL.value = url
    }

    fun saveUAString(uas: String) {
        val editor = prefs.edit()
        editor.putString(USER_AGENT_PREF_KEY, uas)
        editor.apply()
        uaString.postValue(uas)
    }

    fun saveAutoCheckUpdates(need: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(AUTOCHECK_UPDATES_KEY, need)
        editor.apply()
        needAutockeckUpdates = need
    }

    fun saveUpdateChannel(selectedChannel: String) {
        val editor = prefs.edit()
        editor.putString(UPDATE_CHANNEL_KEY, selectedChannel)
        editor.apply()
        updateChannel = selectedChannel
    }

    fun checkUpdate(force: Boolean, onDoneCallback: () -> Unit) = GlobalScope.launch(Dispatchers.Main) {
        if (updateChecker.versionCheckResult == null || force) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    updateChecker.check("https://raw.githubusercontent.com/truefedex/tv-bro/master/latest_version.json",
                            arrayOf(updateChannel))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.join()
        }
        onDoneCallback()
    }

    public fun showUpdateDialogIfNeeded(activity: MainActivity, force: Boolean = false) {
        val now = Calendar.getInstance()
        if (lastUpdateNotificationTime.sameDay(now) && !force) {
            return
        }
        if (!updateChecker.hasUpdate()) {
            throw IllegalStateException()
        }
        lastUpdateNotificationTime = now
        prefs.edit().putLong(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY, lastUpdateNotificationTime.timeInMillis).apply()

        updateChecker.showUpdateDialog(activity, "release", object : UpdateChecker.DialogCallback {
            override fun download() {
                updateChecker.versionCheckResult ?: return

                val canInstallFromOtherSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.packageManager.canRequestPackageInstalls()
                } else
                    Settings.Secure.getInt(activity.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1

                if (canInstallFromOtherSources) {
                    /*val filename = "update${update.latestVersionName}.apk"
                    activity.onDownloadRequested(update.url, filename, "tvbro-update-checker",
                            Download.OperationAfterDownload.INSTALL)*/
                    updateChecker.downloadUpdate(activity)
                } else {
                    AlertDialog.Builder(activity)
                            .setTitle(R.string.app_name)
                            .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                R.string.turn_on_unknown_sources_for_app else R.string.turn_on_unknown_sources)
                            .setPositiveButton(android.R.string.ok) { dialog, which ->
                                run {
                                    val intent = Intent()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        intent.action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                        intent.data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                                    } else {
                                        intent.action = Settings.ACTION_SECURITY_SETTINGS
                                    }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_UNKNOWN_APP_SOURCES)
                                        needToShowUpdateDlgAgain = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel) { dialog, which ->

                            }
                            .show()
                }
            }

            override fun later() {}

            override fun settings() {
                activity.showSettings()
            }
        })
    }
}