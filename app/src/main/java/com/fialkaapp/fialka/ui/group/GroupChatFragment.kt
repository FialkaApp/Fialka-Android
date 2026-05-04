/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentGroupChatBinding
import com.fialkaapp.fialka.data.model.GroupMember
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.ui.chat.ChatItem
import com.fialkaapp.fialka.ui.chat.MessagesAdapter

class GroupChatFragment : Fragment() {

    private var _binding: FragmentGroupChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter

    private var groupId: String = ""
    private var groupName: String = ""

    // Accumulated sender name map (publicKey → displayName)
    private val senderNames = mutableMapOf<String, String>()

    // Reply state
    private var replyToId: String? = null
    private var replyToPreview: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupId   = arguments?.getString("groupId")   ?: ""
        groupName = arguments?.getString("groupName") ?: "Groupe"

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvGroupName.text = groupName

        // Toolbar click → GroupInfoFragment
        binding.toolbarContent.setOnClickListener {
            val bundle = Bundle().apply {
                putString("groupId", groupId)
                putString("groupName", groupName)
            }
            findNavController().navigate(R.id.action_groupChat_to_groupInfo, bundle)
        }

        // Messages adapter (group mode: senderNamesMap enabled)
        adapter = MessagesAdapter(
            onQuoteClick = { id ->
                // Scroll to quoted message
                val pos = adapter.currentList.indexOfFirst {
                    it is ChatItem.Message && it.message.localId == id
                }
                if (pos >= 0) binding.rvMessages.smoothScrollToPosition(pos)
            },
            onMessageLongPress = { _, msg ->
                if (!msg.isMine && !msg.isInfoMessage) {
                    setReply(msg)
                }
            }
        )
        adapter.senderNamesMap = senderNames

        val layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter

        // Load members + build sender name map
        viewModel.getMembersLive(groupId).observe(viewLifecycleOwner) { members ->
            updateMemberCount(members)
            senderNames.clear()
            members.forEach { senderNames[it.publicKey] = it.displayName }
            adapter.senderNamesMap = senderNames.toMap()
            // Force rebind: DiffUtil won't rebind items whose message data hasn't changed,
            // but we need to re-render sender labels now that the name map is available.
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        // Observe messages
        viewModel.getMessages(groupId).observe(viewLifecycleOwner) { messages ->
            val items = messages.map { msg ->
                if (msg.isInfoMessage) ChatItem.InfoMessage(msg.plaintext, msg.timestamp)
                else ChatItem.Message(msg)
            }
            adapter.submitList(items) {
                if (items.isNotEmpty()) binding.rvMessages.scrollToPosition(items.size - 1)
            }
        }

        viewModel.clearUnread(groupId)

        // Send button
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        // Cancel reply
        binding.btnCancelReply.setOnClickListener { clearReply() }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) return
        viewModel.sendMessage(groupId, text, replyToId, replyToPreview)
        binding.etMessage.setText("")
        clearReply()
    }

    private fun setReply(msg: MessageLocal) {
        replyToId = msg.localId
        replyToPreview = msg.plaintext.take(80)
        binding.replyPreview.visibility = View.VISIBLE
        val sender = senderNames[msg.senderPublicKey] ?: ""
        binding.tvReplyPreview.text = if (sender.isNotEmpty()) "↩ $sender: $replyToPreview"
                                       else "↩ $replyToPreview"
    }

    private fun clearReply() {
        replyToId = null
        replyToPreview = null
        binding.replyPreview.visibility = View.GONE
    }

    private fun updateMemberCount(members: List<GroupMember>) {
        binding.tvMemberCount.text = "${members.size} membres"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
