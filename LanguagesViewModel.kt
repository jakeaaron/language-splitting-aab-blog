package youversion.bible.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.databinding.ObservableInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import javax.inject.Inject
import youversion.bible.Settings
import youversion.bible.api.model.Localization
import youversion.bible.base.receiver.Broadcasts
import youversion.bible.navigation.di.BaseNavigationController
import youversion.bible.repository.AppLocalizationRepository
import youversion.bible.repository.BibleLocalizationRepository
import youversion.bible.repository.MomentsLocalizationRepository
import youversion.bible.repository.PlansLocalizationRepository
import youversion.bible.util.ContextUtil
import youversion.bible.viewmodel.BaseViewModel

class LanguagesViewModel
@Inject constructor(
    private val appLocalizationRepository: AppLocalizationRepository,
    private val bibleLocalizationRepository: BibleLocalizationRepository,
    private val plansLocalizationRepository: PlansLocalizationRepository,
    private val momentsLocalizationRepository: MomentsLocalizationRepository
) : BaseViewModel() {

    private var receiver: BroadcastReceiver? = null
    private val downloadProgressListeners = mutableListOf<LanguageDownloadProgressListener>()
    private val languagesData = MutableLiveData<List<Localization>>()
    val languages: LiveData<List<Localization>> = languagesData
    val languageTagDownloadingMap = mutableMapOf<String, Pair<ObservableInt, ObservableInt>>()

    private fun onLocalizations(localizations: Collection<Localization>?) {
        languagesData.value = localizations?.toMutableList()?.sortedBy { it.name } ?: emptyList()
    }

    fun loadLanguages(listType: Int) {
        when (listType) {
            BaseNavigationController.LANGUAGE_LIST_APP -> {
                onLoading(appLocalizationRepository.getAppLocalizations())
                    .addCallback { result, _, _ -> onLocalizations(result?.values) }
                registerLanguageProgressDownloadReceiver()
            }
            BaseNavigationController.LANGUAGE_LIST_PLANS ->
                onLoading(plansLocalizationRepository.getPlansLocalizations())
                    .addCallback { result, _, _ -> onLocalizations(result?.values) }
            BaseNavigationController.LANGUAGE_LIST_INITIAL ->
                onLoading(bibleLocalizationRepository.getInitialLocalizations(Settings.country))
                    .addCallback { result, _, _ -> onLocalizations(result) }
            BaseNavigationController.LANGUAGE_LIST_VOTD ->
                onLoading(momentsLocalizationRepository.getVotdLocalizations())
                    .addCallback { result, _, _ -> onLocalizations(result?.values) }
            else -> throw IllegalArgumentException()
        }
    }

    private fun registerLanguageProgressDownloadReceiver() {
        val filter = IntentFilter(Broadcasts.ACTION_LANGUAGE_DOWNLOAD_PROGRESS)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    val languageTag = intent.getStringExtra(Broadcasts.LANGUAGE_TAG) ?: return
                    when (intent.action) {
                        Broadcasts.ACTION_LANGUAGE_DOWNLOAD_PROGRESS -> {
                            val total = intent.getLongExtra(Broadcasts.TOTAL, 0L)
                            val progress = intent.getLongExtra(Broadcasts.PROGRESS, 0L)
                            if (languageTagDownloadingMap.containsKey(languageTag)) {
                                val pair = languageTagDownloadingMap[languageTag]
                                pair?.first?.set(progress.toInt())
                                pair?.second?.set(total.toInt())
                            } else {
                                languageTagDownloadingMap[languageTag] =
                                    Pair(ObservableInt(progress.toInt()), ObservableInt(total.toInt()))
                                notifyLanguageDownloadListeners(languageTag)
                            }
                        }
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(ContextUtil.context).registerReceiver(receiver!!, filter)
    }

    fun registerLanguageDownloadListener(listener: LanguageDownloadProgressListener) {
        downloadProgressListeners.add(listener)
    }

    fun unregisterLanguageDownloadListener(listener: LanguageDownloadProgressListener) {
        downloadProgressListeners.remove(listener)
    }

    fun notifyLanguageDownloadListeners(languageTag: String?) {
        downloadProgressListeners.forEach { it.onDownloadStarted(languageTag) }
    }

    interface LanguageDownloadProgressListener {
        fun onDownloadStarted(languageTag: String?)
    }

    override fun onCleared() {
        super.onCleared()
        receiver?.let {
            LocalBroadcastManager.getInstance(ContextUtil.context).unregisterReceiver(it)
        }
        receiver = null
    }
}
