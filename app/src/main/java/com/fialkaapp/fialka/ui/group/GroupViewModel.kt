/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fialkaapp.fialka.data.model.Contact
import com.fialkaapp.fialka.data.model.GroupLocal
import com.fialkaapp.fialka.data.model.GroupMember
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.repository.GroupRepository
import kotlinx.coroutines.launch

class GroupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GroupRepository.getInstance(app)
    private val db   = FialkaDatabase.getInstance(app)

    // ── All groups (for ConversationsFragment) ────────────────────────────
    val allGroups: LiveData<List<GroupLocal>> = repo.getAllGroups()

    // ── Create group ──────────────────────────────────────────────────────
    private val _createResult = MutableLiveData<GroupLocal?>()
    val createResult: LiveData<GroupLocal?> = _createResult

    private val _isCreating = MutableLiveData(false)
    val isCreating: LiveData<Boolean> = _isCreating

    fun createGroup(name: String, selectedContacts: List<Contact>) {
        if (name.isBlank() || selectedContacts.isEmpty()) return
        viewModelScope.launch {
            _isCreating.value = true
            try {
                val group = repo.createGroup(name.trim(), selectedContacts)
                _createResult.value = group
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun onCreateHandled() { _createResult.value = null }

    // ── Per-group chat ────────────────────────────────────────────────────
    private var currentGroupId: String = ""

    private val _currentGroup = MutableLiveData<GroupLocal?>()
    val currentGroup: LiveData<GroupLocal?> = _currentGroup

    private val _members = MutableLiveData<List<GroupMember>>(emptyList())
    val members: LiveData<List<GroupMember>> = _members

    fun loadGroup(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            _currentGroup.value = repo.getGroup(groupId)
            _members.value = repo.getMembers(groupId)
        }
    }

    fun getMessages(groupId: String): LiveData<List<MessageLocal>> =
        repo.getMessagesForGroup(groupId)

    fun getMembersLive(groupId: String): LiveData<List<GroupMember>> =
        repo.getMembersLive(groupId)

    fun sendMessage(groupId: String, plaintext: String,
                    replyToId: String? = null, replyToPreview: String? = null) {
        viewModelScope.launch {
            repo.sendGroupMessage(groupId, plaintext, replyToId, replyToPreview)
        }
    }

    fun clearUnread(groupId: String) { repo.clearUnread(groupId) }

    // ── Admin actions ─────────────────────────────────────────────────────
    private val _adminResult = MutableLiveData<String?>()
    val adminResult: LiveData<String?> = _adminResult

    fun kickMember(groupId: String, targetPubKey: String) {
        viewModelScope.launch {
            repo.kickMember(groupId, targetPubKey)
            refreshMembers(groupId)
        }
    }

    fun promoteMember(groupId: String, targetPubKey: String) {
        viewModelScope.launch {
            repo.promoteMember(groupId, targetPubKey)
            refreshMembers(groupId)
        }
    }

    fun demoteMember(groupId: String, targetPubKey: String) {
        viewModelScope.launch {
            repo.demoteMember(groupId, targetPubKey)
            refreshMembers(groupId)
        }
    }

    fun updateInvitePolicy(groupId: String, policy: String) {
        viewModelScope.launch {
            repo.updateInvitePolicy(groupId, policy)
            _currentGroup.value = repo.getGroup(groupId)
        }
    }

    fun inviteMember(groupId: String, contact: Contact) {
        viewModelScope.launch { repo.inviteMember(groupId, contact) }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            repo.leaveGroup(groupId)
            _adminResult.value = "left"
        }
    }

    fun onAdminResultHandled() { _adminResult.value = null }

    fun renameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            repo.renameGroup(groupId, newName)
            _currentGroup.value = repo.getGroup(groupId)
        }
    }

    private suspend fun refreshMembers(groupId: String) {
        _members.value = repo.getMembers(groupId)
    }

    // ── Contact list (for CreateGroupFragment) ────────────────────────────
    val contacts: LiveData<List<Contact>> = db.contactDao().getAllContacts()
}
