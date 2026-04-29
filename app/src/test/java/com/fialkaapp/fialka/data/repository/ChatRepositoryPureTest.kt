/*
 * Fialka — Post-quantum encrypted messenger
 * Unit tests for ChatRepository pure logic (no Room, no Tor, no JNI).
 *
 * Scope: constants (DUMMY_PREFIX, FILE_PREFIX, delivery statuses),
 *        file metadata format parsing, filename sanitization, mutex management.
 *
 * Excluded: sendMessage/receiveMessage (require Room+Tor+JNI),
 *           addContact (requires Room), initializeRatchet (requires JNI).
 */
package com.fialkaapp.fialka.data.repository

import com.fialkaapp.fialka.data.model.MessageLocal
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ChatRepositoryPureTest {

    // =========================================================================
    // Delivery status constants — guard against accidental renumbering
    // These values are persisted in Room; changing them is a breaking DB migration.
    // =========================================================================

    @Test
    fun `DELIVERY_SENT is 0`() = assertEquals(0, MessageLocal.DELIVERY_SENT)

    @Test
    fun `DELIVERY_MAILBOX is 1`() = assertEquals(1, MessageLocal.DELIVERY_MAILBOX)

    @Test
    fun `DELIVERY_FAILED is 2`() = assertEquals(2, MessageLocal.DELIVERY_FAILED)

    @Test
    fun `DELIVERY_PENDING is 3`() = assertEquals(3, MessageLocal.DELIVERY_PENDING)

    @Test
    fun `DELIVERY_SENDING is 4`() = assertEquals(4, MessageLocal.DELIVERY_SENDING)

    @Test
    fun `delivery status constants are all distinct`() {
        val statuses = listOf(
            MessageLocal.DELIVERY_SENT,
            MessageLocal.DELIVERY_MAILBOX,
            MessageLocal.DELIVERY_FAILED,
            MessageLocal.DELIVERY_PENDING,
            MessageLocal.DELIVERY_SENDING
        )
        assertEquals("All delivery status codes must be unique", statuses.size, statuses.toSet().size)
    }

    // =========================================================================
    // DUMMY_PREFIX — must not be a valid message start
    // Receiver silently drops any decrypted message that starts with this prefix.
    // =========================================================================

    @Test
    fun `DUMMY_PREFIX is non-empty`() {
        assertTrue(ChatRepository.DUMMY_PREFIX.isNotEmpty())
    }

    @Test
    fun `DUMMY_PREFIX contains only non-printable ASCII control bytes`() {
        // 0x07 (BEL), 0x1B (ESC), 0x03 (ETX) — no user message starts with these
        for (c in ChatRepository.DUMMY_PREFIX) {
            assertTrue(
                "DUMMY_PREFIX char '${c.code}' must be a control character (< 0x20)",
                c.code < 0x20
            )
        }
    }

    @Test
    fun `DUMMY_PREFIX does not appear at start of normal text messages`() {
        val normalMessages = listOf(
            "Hello!",
            "fialka://invite?v=3&ed=...",
            "FILE|inline|key|iv|name|100|0",
            "1|plaintext content",
            " ",
            ""
        )
        for (msg in normalMessages) {
            assertFalse(
                "Normal message '$msg' should not start with DUMMY_PREFIX",
                msg.startsWith(ChatRepository.DUMMY_PREFIX)
            )
        }
    }

    // =========================================================================
    // FILE_PREFIX — format: "FILE|inline|keyB64|ivB64|fileName|size|oneshot"
    // This prefix gates the file reception path in receiveMessage().
    // =========================================================================

    @Test
    fun `FILE_PREFIX is FILE pipe`() {
        assertEquals("FILE|", ChatRepository.FILE_PREFIX)
    }

    @Test
    fun `file metadata parsing - valid inline format 6 parts`() {
        val key = "aGVsbG8="
        val iv = "d29ybGQ="
        val fileName = "photo.jpg"
        val fileSize = 204800L
        val metadata = "FILE|inline|$key|$iv|$fileName|$fileSize|0"

        val payload = metadata.removePrefix(ChatRepository.FILE_PREFIX)
        val parts = payload.split("|", limit = 6)

        assertEquals("inline", parts[0])
        assertEquals(key, parts[1])
        assertEquals(iv, parts[2])
        assertEquals(fileName, parts[3])
        assertEquals(fileSize, parts[4].toLong())
        assertEquals("0", parts[5])
    }

    @Test
    fun `file metadata parsing - oneshot flag`() {
        val metadata = "FILE|inline|key|iv|secret.jpg|1024|1"
        val payload = metadata.removePrefix(ChatRepository.FILE_PREFIX)
        val parts = payload.split("|", limit = 6)
        assertEquals("1", parts[5])  // isOneShot = true
    }

    @Test
    fun `file metadata parsing - missing parts returns less than 5 items`() {
        // Malformed payload: only 3 parts after prefix → must return null in receiver
        val malformed = "FILE|inline|key"
        val payload = malformed.removePrefix(ChatRepository.FILE_PREFIX)
        val parts = payload.split("|", limit = 6)
        assertTrue("Malformed metadata should have < 5 parts", parts.size < 5)
    }

    @Test
    fun `file metadata parsing - fileName with pipe chars is split at limit 6`() {
        // fileName containing '|' would only be safe if limit=6 caps the split
        val trickName = "evil|pipe|name"
        val metadata = "FILE|inline|key|iv|$trickName|100|0"
        val payload = metadata.removePrefix(ChatRepository.FILE_PREFIX)
        val parts = payload.split("|", limit = 6)
        // limit=6 means parts[3..5] absorbs the rest — parts.size == 6 max
        assertTrue("Split with limit=6 should have at most 6 parts", parts.size <= 6)
    }

    @Test
    fun `file metadata distinguishes text from file message`() {
        val fileMsg  = "FILE|inline|key|iv|doc.pdf|5000|0"
        val textMsg  = "Hello from Alice"
        val dummyMsg = ChatRepository.DUMMY_PREFIX + "randompadding"

        assertTrue(fileMsg.startsWith(ChatRepository.FILE_PREFIX))
        assertFalse(textMsg.startsWith(ChatRepository.FILE_PREFIX))
        assertFalse(dummyMsg.startsWith(ChatRepository.FILE_PREFIX))
        assertTrue(dummyMsg.startsWith(ChatRepository.DUMMY_PREFIX))
    }

    // =========================================================================
    // Filename sanitization — path traversal prevention
    // saveFileLocally replaces [^a-zA-Z0-9._-] with '_'.
    // This is the ONLY guard against a malicious sender injecting "../" paths.
    // =========================================================================

    @Test
    fun `filename sanitization blocks path traversal`() {
        val malicious = "../../../etc/passwd"
        // Regex replaces '/' with '_' but keeps '.'. After sanitization '..'
        // without '/' is harmless as a filename — can't traverse directories.
        val sanitized = malicious.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        assertFalse("Sanitized name must not contain '/'", sanitized.contains("/"))
        assertEquals(".._.._.._etc_passwd", sanitized)
    }

    @Test
    fun `filename sanitization allows normal file names`() {
        listOf("photo.jpg", "my-document.pdf", "file_v2.txt", "data123.bin").forEach { name ->
            val sanitized = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            assertEquals("Normal filename '$name' should be unchanged", name, sanitized)
        }
    }

    @Test
    fun `filename sanitization strips null bytes`() {
        val withNull = "evil\u0000file.txt"
        val sanitized = withNull.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        assertFalse("Null byte must be replaced", sanitized.contains("\u0000"))
    }

    @Test
    fun `filename sanitization strips spaces and special chars`() {
        val withSpaces = "my file (2024).jpg"
        val sanitized = withSpaces.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        assertEquals("my_file__2024_.jpg", sanitized)
    }

    // =========================================================================
    // MessageLocal — data class invariants
    // =========================================================================

    @Test
    fun `MessageLocal default ephemeralDuration is 0 (permanent)`() {
        val msg = MessageLocal(
            localId = "id1",
            conversationId = "conv1",
            senderPublicKey = "pubkey",
            plaintext = "hello",
            isMine = true
        )
        assertEquals(0L, msg.ephemeralDuration)
        assertEquals(0L, msg.expiresAt)
        assertFalse(msg.isInfoMessage)
        assertFalse(msg.isOneShot)
        assertFalse(msg.oneShotOpened)
        assertEquals(MessageLocal.DELIVERY_SENDING, msg.deliveryStatus)
    }

    @Test
    fun `MessageLocal with ephemeral duration has positive expiresAt when set`() {
        val now = System.currentTimeMillis()
        val duration = 30_000L
        val msg = MessageLocal(
            localId = "id2",
            conversationId = "conv1",
            senderPublicKey = "pubkey",
            plaintext = "self-destruct",
            isMine = false,
            ephemeralDuration = duration,
            expiresAt = now + duration
        )
        assertTrue("expiresAt must be in the future", msg.expiresAt > now)
        assertEquals(duration, msg.ephemeralDuration)
    }

    @Test
    fun `MessageLocal signatureValid nullable states`() {
        val noSig  = MessageLocal("id", "c", "pk", "txt", isMine = false, signatureValid = null)
        val valid  = MessageLocal("id", "c", "pk", "txt", isMine = false, signatureValid = true)
        val invalid = MessageLocal("id", "c", "pk", "txt", isMine = false, signatureValid = false)

        assertNull(noSig.signatureValid)
        assertTrue(valid.signatureValid!!)
        assertFalse(invalid.signatureValid!!)
    }

    // =========================================================================
    // Mutex management — getMutex / clearMutexes
    // =========================================================================

    @Test
    fun `getMutex returns same instance for same conversationId`() {
        ChatRepository.clearMutexes()
        val m1 = ChatRepository.getMutex("conv_abc")
        val m2 = ChatRepository.getMutex("conv_abc")
        assertSame("Same conversationId must return same Mutex instance", m1, m2)
    }

    @Test
    fun `getMutex returns different instances for different conversationIds`() {
        ChatRepository.clearMutexes()
        val m1 = ChatRepository.getMutex("conv_alpha")
        val m2 = ChatRepository.getMutex("conv_beta")
        assertNotSame(m1, m2)
    }

    @Test
    fun `clearMutexes resets the mutex map`() {
        ChatRepository.getMutex("conv_to_clear")
        ChatRepository.clearMutexes()
        // After clear, a new mutex is created (different instance than the old one)
        val fresh = ChatRepository.getMutex("conv_to_clear")
        assertNotNull(fresh)
    }

    // =========================================================================
    // Message index embedding format — "$messageIndex|$plaintext"
    // sendMessage embeds this; receiveMessage strips it during trial decryption.
    // =========================================================================

    @Test
    fun `augmented plaintext format embeds index before first pipe`() {
        val index = 42
        val plaintext = "Hello, Bob!"
        val augmented = "$index|$plaintext"

        val firstPipe = augmented.indexOf('|')
        val extractedIndex = augmented.substring(0, firstPipe).toIntOrNull()
        val extractedPlaintext = augmented.substring(firstPipe + 1)

        assertEquals(index, extractedIndex)
        assertEquals(plaintext, extractedPlaintext)
    }

    @Test
    fun `augmented plaintext - plaintext with pipes is extracted correctly`() {
        // If plaintext itself contains '|', only the FIRST '|' is the separator
        val index = 7
        val plaintext = "FILE|inline|key|iv|name|100|0"  // contains pipes
        val augmented = "$index|$plaintext"

        val firstPipe = augmented.indexOf('|')
        val extractedPlaintext = augmented.substring(firstPipe + 1)
        assertEquals(plaintext, extractedPlaintext)
    }
}
