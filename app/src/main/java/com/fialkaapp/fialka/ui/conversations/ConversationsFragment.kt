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
package com.fialkaapp.fialka.ui.conversations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentConversationsBinding
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import com.fialkaapp.fialka.util.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.fialkaapp.fialka.wallet.WalletPreferences
import kotlinx.coroutines.launch

/**
 * Conversations list screen — shows all active chats + pending contact requests.
 * FAB to add a new contact, toolbar menu to view profile or reset account.
 */
class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by viewModels()

    // Conversations tab adapter (page 1)
    private lateinit var conversationsAdapter: ConversationsAdapter
    // Groups tab adapter (page 0)
    private lateinit var groupsAdapter: GroupsAdapter
    private lateinit var requestsAdapter: ContactRequestsAdapter

    private var allConversations: List<com.fialkaapp.fialka.data.model.Conversation> = emptyList()

    // Page view references — set once in onViewCreated
    private var rvChatsPage: RecyclerView? = null
    private var tvChatsEmpty: TextView? = null

    /** Android 13+ runtime permission for POST_NOTIFICATIONS. */
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Permission just granted — re-issue the foreground service notification
                // so it appears immediately without needing a kill + relaunch.
                com.fialkaapp.fialka.tor.TorForegroundService.renotify(requireContext())
            } else {
                // User denied — explain why it matters
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.perm_notif_denied_title))
                    .setMessage(getString(R.string.perm_notif_denied_message))
                    .setPositiveButton(getString(R.string.perm_open_settings)) { _, _ ->
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        })
                    }
                    .setNegativeButton(getString(R.string.perm_later), null)
                    .show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ask for runtime permissions needed for notifications and background service
        askPermissionsIfNeeded()

        // ── Inflate pager pages ─────────────────────────────────────────────
        val groupsPageView = LayoutInflater.from(requireContext())
            .inflate(R.layout.page_conversations_groups, binding.viewPager, false)
        val chatsPageView = LayoutInflater.from(requireContext())
            .inflate(R.layout.page_conversations_chats, binding.viewPager, false)

        val rvGroupsPage = groupsPageView.findViewById<RecyclerView>(R.id.rvGroupsPage)
        rvChatsPage      = chatsPageView.findViewById(R.id.rvChatsPage)
        tvChatsEmpty     = chatsPageView.findViewById(R.id.tvChatsEmpty)

        // ── Conversations adapter (chats page) ──────────────────────────────
        conversationsAdapter = ConversationsAdapter { conversation ->
            val bundle = Bundle().apply {
                putString("conversationId", conversation.conversationId)
                putString("contactName", conversation.contactDisplayName)
            }
            findNavController().navigate(R.id.action_conversations_to_chat, bundle)
        }
        rvChatsPage!!.adapter = conversationsAdapter

        // ── Groups adapter (groups page) ────────────────────────────────────
        groupsAdapter = GroupsAdapter { group ->
            val bundle = Bundle().apply {
                putString("groupId", group.groupId)
                putString("groupName", group.name)
            }
            findNavController().navigate(R.id.action_conversations_to_groupChat, bundle)
        }
        rvGroupsPage.adapter = groupsAdapter

        // ── ViewPager2 setup ────────────────────────────────────────────────
        // Order: Conversations (0) | Groupes (1)
        binding.viewPager.adapter = ConversationsPagerAdapter(listOf(chatsPageView, groupsPageView))
        binding.viewPager.offscreenPageLimit = 1   // Keep both pages alive

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Conversations" else "Groupes"
            tab.icon = ContextCompat.getDrawable(
                requireContext(),
                if (position == 0) R.drawable.ic_conversation else R.drawable.ic_group
            )
        }.attach()

        // ── SwipeRefresh ────────────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.primary),
            ContextCompat.getColor(requireContext(), R.color.accent)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.grey_medium)
        )
        // Only trigger pull-to-refresh when the active page's list is at the top
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val rv = if (binding.viewPager.currentItem == 0) rvChatsPage else rvGroupsPage
            rv?.canScrollVertically(-1) == true
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
            if (!refreshing) rvChatsPage?.scheduleLayoutAnimation()
        }

        // ── Contact requests adapter ────────────────────────────────────────
        requestsAdapter = ContactRequestsAdapter(
            onAccept  = { request -> viewModel.acceptRequest(request) },
            onDecline = { request -> viewModel.declineRequest(request) }
        )
        binding.rvRequests.adapter = requestsAdapter

        // ── Observe conversations ───────────────────────────────────────────
        viewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            allConversations = conversations
            val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
            val searchView = searchItem?.actionView as? SearchView
            filterConversations(searchView?.query?.toString().orEmpty())
        }

        // ── Search ──────────────────────────────────────────────────────────
        updateWalletMenuVisibility()
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = "Rechercher…"
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterConversations(newText.orEmpty())
                return true
            }
        })
        searchView?.setOnCloseListener {
            filterConversations("")
            false
        }

        // ── Observe pending contact requests ────────────────────────────────
        viewModel.pendingRequests.observe(viewLifecycleOwner) { requests ->
            val hasRequests = requests.isNotEmpty()
            binding.tvRequestsHeader.visibility = if (hasRequests) View.VISIBLE else View.GONE
            binding.rvRequests.visibility = if (hasRequests) View.VISIBLE else View.GONE
            requestsAdapter.submitList(requests)
        }

        // ── Observe groups ──────────────────────────────────────────────────
        viewModel.groups.observe(viewLifecycleOwner) { groups ->
            groupsAdapter.submitList(groups)
            val tvGroupsEmpty = groupsPageView.findViewById<TextView>(R.id.tvGroupsEmpty)
            tvGroupsEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        binding.fabNewChat.setOnClickListener {
            val items = arrayOf(getString(R.string.fab_new_conversation), getString(R.string.fab_new_group))
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.conversations_new_label))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> findNavController().navigate(R.id.action_conversations_to_addContact)
                        1 -> findNavController().navigate(R.id.action_conversations_to_createGroup)
                    }
                }
                .show()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_wallet -> {
                    findNavController().navigate(R.id.action_conversations_to_walletHome)
                    true
                }
                R.id.action_profile -> {
                    findNavController().navigate(R.id.action_conversations_to_profile)
                    true
                }
                R.id.action_settings -> {
                    findNavController().navigate(R.id.action_conversations_to_settings)
                    true
                }
                R.id.action_reset_account -> {
                    showResetAccountDialog()
                    true
                }
                else -> false
            }
        }

        viewModel.accountReset.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                viewModel.onAccountResetHandled()
                findNavController().navigate(R.id.action_conversations_to_onboarding)
            }
        }

        // Tor indicator in toolbar subtitle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    binding.toolbar.subtitle = when (state) {
                        is TorState.ONION_PUBLISHED -> {
                            val a = state.address
                            "\uD83D\uDD12 \uD83E\uDDC5 ${a.take(8)}…${a.takeLast(8)}"
                        }
                        is TorState.CONNECTED -> getString(R.string.conv_tor_connected_badge)
                        is TorState.BOOTSTRAPPING -> "\uD83D\uDD12 \uD83E\uDDC5 Tor ${state.percent}%"
                        is TorState.PUBLISHING_ONION -> "\uD83D\uDD12 \uD83E\uDDC5 .onion…"
                        else -> getString(R.string.conv_e2ee_badge)
                    }
                }
            }
        }
    }

    private fun updateEmptyState(noConversations: Boolean) {
        tvChatsEmpty?.visibility = if (noConversations) View.VISIBLE else View.GONE
        rvChatsPage?.visibility  = if (noConversations) View.GONE else View.VISIBLE
    }

    private fun filterConversations(query: String) {
        val filtered = if (query.isBlank()) {
            allConversations
        } else {
            val q = query.trim().lowercase()
            allConversations.filter { it.contactDisplayName.lowercase().contains(q) }
        }
        conversationsAdapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun showResetAccountDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.delete_account_confirm) { _, _ ->
                viewModel.resetAccount()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Ask for runtime permissions needed for the app to work correctly.
     *
     * POST_NOTIFICATIONS is now requested earlier, in TorBootstrapFragment when the
     * .onion is published — so users see the notification appear at the right moment.
     * Here we only handle the fallback (user denied during bootstrap) and battery opt.
     */
    private fun askPermissionsIfNeeded() {
        val prefs = requireContext().getSharedPreferences("fialka_perm_asked", 0)

        // Fallback: if user denied POST_NOTIFICATIONS during bootstrap and hasn't been
        // asked again, give them one more chance here.
        val needNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationHelper.hasNotificationPermission(requireContext())
                && !prefs.getBoolean("notif_asked_conversations", false)

        val pm = requireContext().getSystemService(PowerManager::class.java)
        val needBattery = !pm.isIgnoringBatteryOptimizations(requireContext().packageName)
                && !prefs.getBoolean("battery_asked", false)

        if (needNotif) {
            prefs.edit().putBoolean("notif_asked_conversations", true).apply()
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needBattery) {
            showBatteryDialog(prefs)
        }
    }

    private fun showBatteryDialog(prefs: android.content.SharedPreferences) {
        prefs.edit().putBoolean("battery_asked", true).apply()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.perm_battery_title))
            .setMessage(getString(R.string.perm_battery_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.perm_allow)) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    })
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton(getString(R.string.perm_later), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateWalletMenuVisibility()
    }

    private fun updateWalletMenuVisibility() {
        binding.toolbar.menu.findItem(R.id.action_wallet)?.isVisible =
            WalletPreferences.isWalletEnabled(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
