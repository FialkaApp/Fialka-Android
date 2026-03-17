package com.securechat.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.databinding.FragmentSettingsSecurityBinding
import com.securechat.util.AppLockManager

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

        setupPin()
        setupBiometric()
        setupAutoLock()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
