/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 *
 * Instrumented tests for the wallet seed lifecycle.
 * Requires a real device or emulator (uses JNI + Android Keystore).
 *
 * Tests are isolated: each @Test wipes the wallet before and after.
 */
package com.fialkaapp.fialka.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fialkaapp.fialka.crypto.WalletSeedManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletSeedManagerTest {

    private val ctx by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun wipeBeforeEachTest() {
        // Start clean — wipeWallet is idempotent
        WalletSeedManager.wipeWallet(ctx)
        WalletSeedManager.setWalletEnabled(ctx, false)
    }

    @After
    fun wipeAfterEachTest() {
        WalletSeedManager.wipeWallet(ctx)
    }

    // =========================================================================
    // Feature flag
    // =========================================================================

    @Test
    fun walletIsDisabledByDefault() {
        assertFalse(WalletSeedManager.isWalletEnabled(ctx))
    }

    @Test
    fun setWalletEnabled_roundtrip() {
        WalletSeedManager.setWalletEnabled(ctx, true)
        assertTrue(WalletSeedManager.isWalletEnabled(ctx))

        WalletSeedManager.setWalletEnabled(ctx, false)
        assertFalse(WalletSeedManager.isWalletEnabled(ctx))
    }

    // =========================================================================
    // Seed presence
    // =========================================================================

    @Test
    fun hasWalletSeed_falseBeforeGeneration() {
        assertFalse(WalletSeedManager.hasWalletSeed(ctx))
    }

    @Test
    fun generateAndStoreNewSeed_seedExists() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        assertTrue(WalletSeedManager.hasWalletSeed(ctx))
    }

    @Test(expected = IllegalStateException::class)
    fun generateAndStoreNewSeed_throwsIfSeedAlreadyExists() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        // Second call must throw
        WalletSeedManager.generateAndStoreNewSeed(ctx)
    }

    // =========================================================================
    // Key derivation
    // =========================================================================

    @Test
    fun deriveAndGetKeys_returnsNullWithNoSeed() {
        assertNull(WalletSeedManager.deriveAndGetKeys(ctx))
    }

    @Test
    fun deriveAndGetKeys_returnsKeysAfterGeneration() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        val keys = WalletSeedManager.deriveAndGetKeys(ctx)
        try {
            assertNotNull(keys)
            assertEquals(32, keys!!.spendPub.size)
            assertEquals(32, keys.viewPub.size)
            assertEquals(32, keys.viewPriv.size)
        } finally {
            keys?.zeroize()
        }
    }

    @Test
    fun deriveAndGetKeys_isDeterministic() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)

        val keys1 = WalletSeedManager.deriveAndGetKeys(ctx)
        val keys2 = WalletSeedManager.deriveAndGetKeys(ctx)
        try {
            assertNotNull(keys1)
            assertNotNull(keys2)
            assertArrayEquals("spendPub must be deterministic", keys1!!.spendPub, keys2!!.spendPub)
            assertArrayEquals("viewPub must be deterministic",  keys1.viewPub,  keys2.viewPub)
            assertArrayEquals("viewPriv must be deterministic", keys1.viewPriv, keys2.viewPriv)
        } finally {
            keys1?.zeroize()
            keys2?.zeroize()
        }
    }

    @Test
    fun deriveAndGetKeys_spendPubAndViewPubAreDistinct() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        val keys = WalletSeedManager.deriveAndGetKeys(ctx)
        try {
            assertNotNull(keys)
            assertFalse(
                "spendPub and viewPub must differ",
                keys!!.spendPub.contentEquals(keys.viewPub)
            )
        } finally {
            keys?.zeroize()
        }
    }

    @Test
    fun deriveAndGetKeys_keysAreZeroizedAfterCall() {
        // zeroize() must overwrite all bytes to 0
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        val keys = WalletSeedManager.deriveAndGetKeys(ctx)!!
        keys.zeroize()
        assertArrayEquals(ByteArray(32), keys.spendPub)
        assertArrayEquals(ByteArray(32), keys.viewPub)
        assertArrayEquals(ByteArray(32), keys.viewPriv)
    }

    // =========================================================================
    // Wipe
    // =========================================================================

    @Test
    fun wipeWallet_removesSeedAndDisablesWallet() {
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        WalletSeedManager.setWalletEnabled(ctx, true)

        WalletSeedManager.wipeWallet(ctx)

        assertFalse(WalletSeedManager.hasWalletSeed(ctx))
        assertFalse(WalletSeedManager.isWalletEnabled(ctx))
        assertNull(WalletSeedManager.deriveAndGetKeys(ctx))
    }

    @Test
    fun wipeWallet_isIdempotent() {
        // Should not throw when called twice
        WalletSeedManager.wipeWallet(ctx)
        WalletSeedManager.wipeWallet(ctx)
        assertFalse(WalletSeedManager.hasWalletSeed(ctx))
    }

    // =========================================================================
    // Two wallets generate different keys
    // =========================================================================

    @Test
    fun twoFreshSeeds_produceDifferentKeys() {
        // Seed 1
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        val keys1 = WalletSeedManager.deriveAndGetKeys(ctx)

        WalletSeedManager.wipeWallet(ctx)

        // Seed 2
        WalletSeedManager.generateAndStoreNewSeed(ctx)
        val keys2 = WalletSeedManager.deriveAndGetKeys(ctx)

        try {
            assertNotNull(keys1)
            assertNotNull(keys2)
            // Two independent seeds must produce different key material
            assertFalse(
                "Two fresh seeds must generate different spendPub keys",
                keys1!!.spendPub.contentEquals(keys2!!.spendPub)
            )
        } finally {
            keys1?.zeroize()
            keys2?.zeroize()
        }
    }
}
