package com.securechat.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentOnboardingBinding

/**
 * Onboarding screen — shown on first launch.
 * Allows the user to choose a display name and generate their cryptographic identity.
 */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If user already exists, skip onboarding
        viewModel.existingUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                findNavController().navigate(R.id.action_onboarding_to_conversations)
            }
        }

        binding.btnCreateIdentity.setOnClickListener {
            val displayName = binding.etDisplayName.text.toString()
            viewModel.createIdentity(displayName)
        }

        binding.btnRestore.setOnClickListener {
            findNavController().navigate(R.id.action_onboarding_to_restore)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is OnboardingViewModel.OnboardingState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreateIdentity.isEnabled = true
                }
                is OnboardingViewModel.OnboardingState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnCreateIdentity.isEnabled = false
                }
                is OnboardingViewModel.OnboardingState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    findNavController().navigate(R.id.action_onboarding_to_backup)
                }
                is OnboardingViewModel.OnboardingState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreateIdentity.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
