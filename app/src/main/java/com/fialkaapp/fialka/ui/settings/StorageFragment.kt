/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fialkaapp.fialka.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.databinding.FragmentStorageBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage Management screen.
 * Shows DB size, cache size, message/file counts.
 * Provides cache purge, file purge, expired message purge, and full message wipe.
 */
class StorageFragment : Fragment() {

    private var _binding: FragmentStorageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.rowClearCache.setOnClickListener { confirmClearCache() }
        binding.rowDeleteFiles.setOnClickListener { confirmDeleteFiles() }
        binding.rowDeleteExpired.setOnClickListener { purgeExpired() }
        binding.rowDeleteAllMessages.setOnClickListener { confirmDeleteAllMessages() }

        refreshStats()
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    private fun refreshStats() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = FialkaDatabase.getInstance(requireContext())
            val msgCount = db.messageLocalDao().getTotalMessageCount()
            val fileCount = db.messageLocalDao().getFileMessageCount()
            val dbSize = getDatabaseSize()
            val cacheSize = getCacheDirSize()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.tvStatMessages.text = msgCount.toString()
                binding.tvStatFiles.text = fileCount.toString()
                binding.tvStatDbSize.text = formatSize(dbSize)
                binding.tvStatCache.text = formatSize(cacheSize)
                showLoading(false)
            }
        }
    }

    private fun getDatabaseSize(): Long {
        return try {
            val ctx = requireContext()
            val dbFile = ctx.getDatabasePath("fialka_database")
            var total = dbFile.length()
            // SQLCipher WAL + SHM
            total += File("${dbFile.path}-wal").length()
            total += File("${dbFile.path}-shm").length()
            total
        } catch (_: Exception) { 0L }
    }

    private fun getCacheDirSize(): Long {
        return try {
            dirSize(requireContext().cacheDir)
        } catch (_: Exception) { 0L }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} o"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} Ko"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} Mo"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} Go"
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vider le cache")
            .setMessage("Cela supprimera les fichiers temporaires (miniatures, données de rendu). Aucun message ne sera effacé.")
            .setPositiveButton("Vider") { _, _ -> clearCache() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun clearCache() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            deleteDirContents(requireContext().cacheDir)
            withContext(Dispatchers.Main) {
                showStatus("Cache vidé.")
                refreshStats()
            }
        }
    }

    private fun confirmDeleteFiles() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val count = FialkaDatabase.getInstance(ctx).messageLocalDao().getFileMessageCount()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Supprimer les fichiers reçus")
                    .setMessage("$count message(s) contenant des fichiers seront effacés. Cette action est irréversible.")
                    .setPositiveButton("Supprimer") { _, _ -> deleteFileMessages() }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
    }

    private fun deleteFileMessages() {
        val ctx = requireContext()
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = FialkaDatabase.getInstance(ctx)
            db.messageLocalDao().deleteFileMessages()
            withContext(Dispatchers.Main) {
                showStatus("Fichiers supprimés.")
                refreshStats()
            }
        }
    }

    private fun purgeExpired() {
        val ctx = requireContext()
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = FialkaDatabase.getInstance(ctx)
            db.messageLocalDao().deleteExpiredMessages(System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                showStatus("Messages expirés supprimés.")
                refreshStats()
            }
        }
    }

    private fun confirmDeleteAllMessages() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer tous les messages ?")
            .setMessage(
                "Cette action effacera définitivement l'historique de TOUTES les conversations.\n\n" +
                "Vos contacts ne seront pas supprimés. Cette action est irréversible."
            )
            .setPositiveButton("Supprimer tout") { _, _ -> deleteAllMessages() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteAllMessages() {
        val ctx = requireContext()
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = FialkaDatabase.getInstance(ctx)
            db.messageLocalDao().deleteAllMessages()
            withContext(Dispatchers.Main) {
                showStatus("Tous les messages ont été supprimés.")
                refreshStats()
            }
        }
    }

    private fun deleteDirContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteDirContents(file)
            file.delete()
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showLoading(visible: Boolean) {
        if (_binding == null) return
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showStatus(msg: String) {
        if (_binding == null) return
        binding.tvStatus.text = msg
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.postDelayed({ binding.tvStatus.visibility = View.GONE }, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
