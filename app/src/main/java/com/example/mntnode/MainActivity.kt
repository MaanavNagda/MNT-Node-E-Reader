package com.example.mntnode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.file.FileFullPath
import com.anggrayudi.storage.file.StorageId
import com.anggrayudi.storage.file.makeFile
import com.anggrayudi.storage.file.openOutputStream
import com.example.mntnode.data.AppDatabase
import com.example.mntnode.data.LibraryRepository
import com.example.mntnode.data.preferences.AppPreferences
import com.example.mntnode.data.preferences.PreferencesRepository
import com.example.mntnode.ui.MNTNodeApp
import com.example.mntnode.ui.StatisticsExportFileNames
import com.example.mntnode.ui.StatisticsExportPdfWriter
import com.example.mntnode.ui.theme.MNTNodeTheme
import com.example.mntnode.ui.theme.ShelfBackground
import com.example.mntnode.R
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val preferencesRepository by lazy { PreferencesRepository(applicationContext) }

    private lateinit var storageHelper: SimpleStorageHelper

    private fun launchAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                },
            )
        }
    }

    private val viewModel: MNTNodeViewModel by viewModels(
        factoryProducer = {
            val db = AppDatabase.get(this@MainActivity)
            val repository = LibraryRepository(this@MainActivity, db)
            MNTNodeViewModelFactory(application, repository, preferencesRepository)
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageHelper = SimpleStorageHelper(this, savedInstanceState)
        storageHelper.onFolderSelected = fun(_: Int, folder: DocumentFile) {
            val payload = viewModel.consumePendingStatisticsFolderExport() ?: return
            lifecycleScope.launch(Dispatchers.IO) {
                val act = this@MainActivity
                val outcome = runCatching {
                    val base = StatisticsExportFileNames.baseName()
                    val txtFile = folder.makeFile(act, "$base.txt", "text/plain")
                        ?: error("could not create .txt")
                    val pdfFile = folder.makeFile(act, "$base.pdf", "application/pdf")
                        ?: error("could not create .pdf")
                    txtFile.openOutputStream(act, append = false)?.use { stream ->
                        stream.write(payload.textForTxtFile.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("could not write .txt")
                    pdfFile.openOutputStream(act, append = false)?.use { stream ->
                        StatisticsExportPdfWriter.write(payload.pdfDocument, stream)
                    } ?: error("could not write .pdf")
                }
                withContext(Dispatchers.Main) {
                    outcome.fold(
                        onSuccess = {
                            viewModel.notifyStatisticsExportMessage(
                                act.getString(R.string.statistics_export_saved),
                            )
                        },
                        onFailure = { e ->
                            viewModel.notifyStatisticsExportMessage(
                                act.getString(
                                    R.string.statistics_export_save_failed,
                                    e.message ?: e.javaClass.simpleName,
                                ),
                            )
                        },
                    )
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            var prefs by remember { mutableStateOf(AppPreferences()) }
            LaunchedEffect(Unit) {
                preferencesRepository.preferencesFlow.collect { prefs = it }
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when {
                prefs.followSystemDarkMode -> systemDark
                prefs.useDayTheme -> false
                else -> true
            }
            val amoled = prefs.useAmoledDark && darkTheme

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    viewModel.scanStorageRoot()
                }
            }

            val activity = this@MainActivity

            MNTNodeTheme(
                darkTheme = darkTheme,
                amoledDark = amoled,
            ) {
                Box(Modifier.fillMaxSize()) {
                    ShelfBackground(
                        isDay = !darkTheme,
                        isAmoledDark = darkTheme && amoled,
                    )
                    MNTNodeApp(
                        viewModel = viewModel,
                        preferencesRepository = preferencesRepository,
                        prefs = prefs,
                        modifier = Modifier.fillMaxSize(),
                        onExportStatistics = {
                            viewModel.requestStatisticsExport { payload ->
                                viewModel.setPendingStatisticsFolderExport(payload)
                                storageHelper.openFolderPicker(
                                    initialPath = FileFullPath(
                                        activity,
                                        StorageId.PRIMARY,
                                        "Documents",
                                    ),
                                )
                            }
                        },
                        onScanDownloads = {
                            when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                    if (Environment.isExternalStorageManager()) {
                                        viewModel.scanStorageRoot()
                                    } else {
                                        launchAllFilesAccessSettings()
                                    }
                                }
                                else -> {
                                    when {
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                        ) == PackageManager.PERMISSION_GRANTED -> {
                                            viewModel.scanStorageRoot()
                                        }
                                        else -> permissionLauncher.launch(
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }
}
