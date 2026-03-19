package com.securechat.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.securechat.databinding.FragmentSettingsSecurityBinding
import com.securechat.tor.TorManager
import com.securechat.tor.TorState
import com.securechat.util.AppLockManager
import com.securechat.util.DeviceSecurityManager
import com.securechat.util.SecurityLevel
import com.securechat.util.StrongBoxStatus
import kotlinx.coroutines.launch

class SecurityFragment : Fragment() {

    private var _binding: FragmentSettingsSecurityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupDeviceSecurity()
        setupPin()
        setupBiometric()
        setupAutoLock()
        setupTor()
    }

    private fun setupDeviceSecurity() {
        val profile = DeviceSecurityManager.getSecurityProfile(requireContext())

        binding.tvOsName.text = profile.osLabel
        binding.tvDeviceName.text = profile.deviceName
        binding.tvSecurityLevel.text = profile.securityLevelLabel

        val badgeColor = when (profile.securityLevel) {
            SecurityLevel.MAXIMUM  -> Color.parseColor("#1DB954")
            SecurityLevel.HIGH     -> Color.parseColor("#4A90D9")
            SecurityLevel.STANDARD -> Color.parseColor("#888780")
        }
        binding.badgeSecurityLevel.backgroundTintList = ColorStateList.valueOf(badgeColor)
        binding.badgeSecurityLevel.text = profile.securityLevelLabel

        binding.tvStrongboxStatus.text = when {
            profile.isGrapheneOS && profile.isStrongBoxAvailable          -> "Titan M2 actif"
            !profile.isGrapheneOS && profile.isStrongBoxAvailable         -> "Secure Element actif"
            profile.strongBoxStatus == StrongBoxStatus.NOT_AVAILABLE      -> "TEE standard (KeyStore)"
            profile.strongBoxStatus == StrongBoxStatus.DECLARED_BUT_UNAVAILABLE -> "Déclaré mais non fonctionnel"
            else                                                           -> "TEE standard (KeyStore)"
        }

        binding.bannerStrongboxNonGos.visibility =
            if (!profile.isGrapheneOS && profile.isStrongBoxAvailable) View.VISIBLE else View.GONE
        binding.bannerSecondaryProfile.visibility =
            if (profile.isSecondaryProfile) View.VISIBLE else View.GONE
    }

    private fun setupAutoLock() {
        binding.layoutAutoLock.setOnClickListener {
            val labels = AppLockManager.AUTO_LOCK_LABELS
            val options = AppLockManager.AUTO_LOCK_OPTIONS
            val currentDelay = AppLockManager.getAutoLockDelay(requireContext())
            val checkedIndex = options.indexOf(currentDelay).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle("Verrouillage automatique")
                .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                    AppLockManager.setAutoLockDelay(requireContext(), options[which])
                    binding.tvAutoLockSummary.text = "Après ${labels[which].lowercase()}"
                    dialog.dismiss()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun setupPin() {
        val pinSet = AppLockManager.isPinSet(requireContext())
        binding.switchPin.isChecked = pinSet
        updatePinUI(pinSet)

        binding.switchPin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showPinSetup(changing = false)
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Désactiver le code")
                    .setMessage("Êtes-vous sûr de vouloir supprimer le code de verrouillage ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        AppLockManager.removePin(requireContext())
                        updatePinUI(false)
                        Toast.makeText(requireContext(), "Code supprimé", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annuler") { _, _ ->
                        binding.switchPin.isChecked = true
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        binding.layoutChangePin.setOnClickListener {
            showPinSetup(changing = true)
        }
    }

    private fun setupBiometric() {
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val bm = BiometricManager.from(requireContext())
                val canAuth = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    AppLockManager.setBiometricEnabled(requireContext(), true)
                    updateBiometricStatus(true)
                } else {
                    binding.switchBiometric.isChecked = false
                    val msg = when (canAuth) {
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Cet appareil n'a pas de capteur biométrique"
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Le capteur biométrique n'est pas disponible"
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Aucune empreinte/visage enregistré dans les paramètres du téléphone"
                        else -> "Biométrie non disponible"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            } else {
                AppLockManager.setBiometricEnabled(requireContext(), false)
                updateBiometricStatus(false)
            }
        }
    }

    private fun showPinSetup(changing: Boolean) {
        val dialog = PinSetupDialogFragment.newInstance(changing)
        dialog.onPinSet = { updatePinUI(true) }
        dialog.show(childFragmentManager, "pin_setup")
    }

    private fun updatePinUI(pinSet: Boolean) {
        binding.switchPin.isChecked = pinSet
        binding.layoutChangePin.visibility = if (pinSet) View.VISIBLE else View.GONE
        binding.layoutAutoLock.visibility = if (pinSet) View.VISIBLE else View.GONE
        binding.dividerAutoLock.visibility = if (pinSet) View.VISIBLE else View.GONE
        binding.tvPinStatus.text = if (pinSet) {
            "✅ Code actif — demandé à chaque ouverture"
        } else {
            "Protégez l'accès à l'application"
        }

        if (pinSet) {
            val label = AppLockManager.getAutoLockLabel(requireContext())
            binding.tvAutoLockSummary.text = "Après ${label.lowercase()}"
            val bm = BiometricManager.from(requireContext())
            val canAuth = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS || canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                binding.tvBiometricHeader.visibility = View.VISIBLE
                binding.layoutBiometric.visibility = View.VISIBLE
                val bioEnabled = AppLockManager.isBiometricEnabled(requireContext())
                binding.switchBiometric.isChecked = bioEnabled
                updateBiometricStatus(bioEnabled)
            } else {
                binding.tvBiometricHeader.visibility = View.GONE
                binding.layoutBiometric.visibility = View.GONE
            }
        } else {
            binding.tvBiometricHeader.visibility = View.GONE
            binding.layoutBiometric.visibility = View.GONE
        }
    }

    private fun updateBiometricStatus(enabled: Boolean) {
        binding.tvBiometricStatus.text = if (enabled) {
            "✅ Activé — utilisez votre empreinte ou visage"
        } else {
            "Empreinte digitale, reconnaissance faciale…"
        }
    }

    private fun setupTor() {
        binding.switchTor.isChecked = TorManager.isTorEnabled()

        binding.switchTor.setOnCheckedChangeListener { _, isChecked ->
            TorManager.setTorEnabled(isChecked)
            binding.layoutTorReconnect.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Show/hide reconnect based on whether Tor is enabled
        binding.layoutTorReconnect.visibility = if (TorManager.isTorEnabled()) View.VISIBLE else View.GONE

        binding.layoutTorReconnect.setOnClickListener {
            binding.torProgressIndicator.visibility = View.VISIBLE
            binding.torProgressIndicator.isIndeterminate = true
            TorManager.restart()
        }

        // Observe Tor state in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    if (_binding == null) return@collect
                    updateTorStatus(state)
                }
            }
        }
    }

    private fun updateTorStatus(state: TorState) {
        when (state) {
            is TorState.IDLE -> {
                binding.tvTorStatus.text = "Tor désactivé"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.STARTING -> {
                binding.tvTorStatus.text = "Démarrage…"
                binding.torProgressIndicator.visibility = View.VISIBLE
                binding.torProgressIndicator.isIndeterminate = true
            }
            is TorState.BOOTSTRAPPING -> {
                binding.tvTorStatus.text = "Connexion… ${state.percent}%"
                binding.torProgressIndicator.visibility = View.VISIBLE
                binding.torProgressIndicator.isIndeterminate = false
                binding.torProgressIndicator.max = 100
                binding.torProgressIndicator.setProgressCompat(state.percent, true)
            }
            is TorState.CONNECTED -> {
                binding.tvTorStatus.text = "✅ Connecté au réseau Tor"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.ERROR -> {
                binding.tvTorStatus.text = "❌ ${state.message}"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.DISCONNECTED -> {
                binding.tvTorStatus.text = "Déconnecté"
                binding.torProgressIndicator.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
