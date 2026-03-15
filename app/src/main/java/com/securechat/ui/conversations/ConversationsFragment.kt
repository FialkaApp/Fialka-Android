package com.securechat.ui.conversations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentConversationsBinding

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

        // Contact requests adapter
        requestsAdapter = ContactRequestsAdapter(
            onAccept = { request -> viewModel.acceptRequest(request) },
            onDecline = { request -> viewModel.declineRequest(request) }
        )
        binding.rvRequests.adapter = requestsAdapter

        // Observe conversations
        viewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            adapter.submitList(conversations)
            updateEmptyState(conversations.isEmpty())
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
    }

    private fun updateEmptyState(noConversations: Boolean) {
        val hasRequests = (viewModel.pendingRequests.value?.size ?: 0) > 0
        val showEmpty = noConversations && !hasRequests
        binding.tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.rvConversations.visibility = if (noConversations) View.GONE else View.VISIBLE
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
