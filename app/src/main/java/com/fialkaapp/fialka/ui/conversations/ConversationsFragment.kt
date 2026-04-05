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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentConversationsBinding
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import kotlinx.coroutines.launch

/**
 * Conversations list screen — shows all active chats + pending contact requests.
 * FAB to add a new contact, toolbar menu to view profile or reset account.
 */
class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var adapter: ConversationsAdapter
    private lateinit var requestsAdapter: ContactRequestsAdapter
    private var allConversations: List<com.fialkaapp.fialka.data.model.Conversation> = emptyList()

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

        // Conversations adapter
        adapter = ConversationsAdapter { conversation ->
            val bundle = Bundle().apply {
                putString("conversationId", conversation.conversationId)
                putString("contactName", conversation.contactDisplayName)
            }
            findNavController().navigate(R.id.action_conversations_to_chat, bundle)
        }
        binding.rvConversations.adapter = adapter

        // Pull-to-refresh: theme the spinner with primary + accent colors, then delegate to ViewModel
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.primary),
            ContextCompat.getColor(requireContext(), R.color.accent)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.grey_medium)
        )
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
            // Replay the fall-in animation when refresh completes so the list feels fresh
            if (!refreshing) {
                binding.rvConversations.scheduleLayoutAnimation()
            }
        }

        // Contact requests adapter
        requestsAdapter = ContactRequestsAdapter(
            onAccept = { request -> viewModel.acceptRequest(request) },
            onDecline = { request -> viewModel.declineRequest(request) }
        )
        binding.rvRequests.adapter = requestsAdapter

        // Observe conversations
        viewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            allConversations = conversations
            // Re-filter with current search query if active
            val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
            val searchView = searchItem?.actionView as? SearchView
            filterConversations(searchView?.query?.toString().orEmpty())
        }

        // Search icon in toolbar — expands to SearchView on click
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

        // Observe pending contact requests
        viewModel.pendingRequests.observe(viewLifecycleOwner) { requests ->
            val hasRequests = requests.isNotEmpty()
            binding.tvRequestsHeader.visibility = if (hasRequests) View.VISIBLE else View.GONE
            binding.rvRequests.visibility = if (hasRequests) View.VISIBLE else View.GONE
            requestsAdapter.submitList(requests)
        }

        binding.fabNewChat.setOnClickListener {
            findNavController().navigate(R.id.action_conversations_to_addContact)
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
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
                        is TorState.CONNECTED -> "\uD83D\uDD12 \uD83E\uDDC5 Tor connecté"
                        is TorState.BOOTSTRAPPING -> "\uD83D\uDD12 \uD83E\uDDC5 Tor ${state.percent}%"
                        is TorState.PUBLISHING_ONION -> "\uD83D\uDD12 \uD83E\uDDC5 .onion…"
                        else -> "\uD83D\uDD12 chiffrée de bout en bout"
                    }
                }
            }
        }
    }

    private fun updateEmptyState(noConversations: Boolean) {
        val hasRequests = (viewModel.pendingRequests.value?.size ?: 0) > 0
        val showEmpty = noConversations && !hasRequests
        binding.tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.rvConversations.visibility = if (noConversations) View.GONE else View.VISIBLE
    }

    private fun filterConversations(query: String) {
        val filtered = if (query.isBlank()) {
            allConversations
        } else {
            val q = query.trim().lowercase()
            allConversations.filter { it.contactDisplayName.lowercase().contains(q) }
        }
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun showResetAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.delete_account_confirm) { _, _ ->
                viewModel.resetAccount()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
