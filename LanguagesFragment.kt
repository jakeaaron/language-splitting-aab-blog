package youversion.bible.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import com.bible.base.ui.R
import com.bible.base.ui.databinding.FragmentSettingsLanguagesBinding
import com.bible.base.ui.databinding.ViewSettingsLanguageListItemBinding
import javax.inject.Inject
import youversion.bible.api.model.Localization
import youversion.bible.base.service.ModuleDownloadService
import youversion.bible.navigation.di.BaseNavigationController
import youversion.bible.ui.BaseFragment
import youversion.bible.ui.BaseFragmentController
import youversion.bible.ui.base.di.BaseUIViewModelFactory
import youversion.bible.ui.viewmodel.LanguagesViewModel
import youversion.bible.util.SplitInstaller
import youversion.bible.widget.Adapter
import youversion.bible.widget.DataBindingViewHolder

class LanguagesFragment : BaseFragment(), LanguagesViewModel.LanguageDownloadProgressListener {

    @Inject
    lateinit var viewModelFactory: BaseUIViewModelFactory

    @Inject
    lateinit var baseNavigationController: BaseNavigationController

    @Inject
    lateinit var viewModel: LanguagesViewModel

    private var adapter: Adapter<Localization>? = null
    private var splitInstaller: SplitInstaller? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_settings_languages, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listType = baseNavigationController.findLanguageList(this)
        val binding = FragmentSettingsLanguagesBinding.bind(view)
        val controller = Controller(this)
        splitInstaller = SplitInstaller(requireActivity())
        adapter = object : Adapter<Localization>(this, context!!, { inflater, parent, _ ->
            val itemBinding = ViewSettingsLanguageListItemBinding.inflate(inflater, parent, false)
            itemBinding.controller = controller
            itemBinding
        }) {
            override fun onBindViewHolder(holder: DataBindingViewHolder<Localization>, position: Int) {
                super.onBindViewHolder(holder, position)
                val localization = holder.item
                val splitLang = ModuleDownloadService.findSplitLanguageTag(localization.id)
                (holder.binding as? ViewSettingsLanguageListItemBinding)?.apply {
                    downloadAllowed = listType == BaseNavigationController.LANGUAGE_LIST_APP
                    val downloadingData =
                        viewModel.languageTagDownloadingMap[splitLang]
                    progressBarProgress = downloadingData?.first
                    progressBarMax = downloadingData?.second
                    downloaded = splitInstaller?.isLanguageInstalled(splitLang) == true
                    executePendingBindings()
                }
            }
        }
        binding.languageList.adapter = adapter

        title = when (listType) {
            BaseNavigationController.LANGUAGE_LIST_APP -> getString(R.string.app_interface)
            BaseNavigationController.LANGUAGE_LIST_PLANS -> getString(R.string.plan_languages)
            BaseNavigationController.LANGUAGE_LIST_INITIAL -> getString(R.string.language)
            BaseNavigationController.LANGUAGE_LIST_VOTD -> getString(R.string.language)
            else -> throw IllegalArgumentException()
        }
        viewModel = viewModelFactory.getLanguagesViewModel(activity!!, listType)
        viewModel.languages.observe(this, Observer { adapter?.list = it })
        viewModel.loadLanguages(listType)
        observe(binding, viewModel)

        viewModel.registerLanguageDownloadListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.languages, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            val listType = baseNavigationController.findLanguageList(this)
            baseNavigationController.startLanguageSearch(this, listType, 1)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 1) {
            data?.let {
                baseNavigationController.findLanguage(it)?.let { localization ->
                    baseNavigationController.finishLanguages(this, localization)
                }
            }
        }
    }

    fun onLanguageSelected(language: Localization?) {
        language?.let {
            val splitTag = ModuleDownloadService.findSplitLanguageTag(it.id)
            if (splitInstaller?.isLanguageInstalled(splitTag) == true)
                baseNavigationController.finishLanguages(this, it)
            else {
                ModuleDownloadService.enqueueWork(
                    requireContext(),
                    baseNavigationController.toDownloadModuleIntent(
                        requireContext(),
                        moduleType = BaseNavigationController.MODULE_TYPE_LANGUAGE,
                        appLanguageTag = ModuleDownloadService.findAppLanguageTag(it.id)
                    )
                )
            }
        }
    }

    private fun onUninstallLanguage(language: Localization?) {
        language?.let {
            val locale = ModuleDownloadService.findLocale(it.id)
            if ((splitInstaller?.getInstalledLanguages()?.size ?: 0) <= 1) // can't uninstall our only language
                return@let
            splitInstaller?.uninstallLanguage(locale)
            Toast.makeText(
                context,
                "Requesting uninstall of ${locale.displayLanguage}. This will happen at some point in the future.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.unregisterLanguageDownloadListener(this)
        adapter = null
    }

    override fun onDownloadStarted(languageTag: String?) {
        languageTag ?: return
        adapter?.let { adapter ->
            (0 until adapter.itemCount)
                .filter {
                    ModuleDownloadService.findSplitLanguageTag(adapter.getItem(it).id) == languageTag
                }
                .forEach {
                    adapter.notifyItemChanged(it)
                }
        }
    }

    companion object {

        class Controller(fragment: LanguagesFragment) : BaseFragmentController<LanguagesFragment>(fragment) {

            fun onLanguageSelected(language: Localization?) {
                fragment?.onLanguageSelected(language)
            }

            fun onUninstallLanguage(language: Localization?) {
                fragment?.onUninstallLanguage(language)
            }
        }
    }
}
