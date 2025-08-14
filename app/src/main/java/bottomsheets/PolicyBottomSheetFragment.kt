package bottomsheets

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.claw.ai.R
import com.claw.ai.databinding.FragmentPolicyBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import models.PolicyData
import models.PolicyType
import models.StringProviderImpl
import models.UiState
import repositories.PolicyRepository
import viewmodels.PolicyViewModel
import viewmodels.PolicyViewModelFactory

class PolicyBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_POLICY_TYPE = "policy_type"

        fun newInstance(policyType: PolicyType): PolicyBottomSheetFragment {
            return PolicyBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POLICY_TYPE, policyType.name)
                }
            }
        }
    }

    private var _binding: FragmentPolicyBottomSheetBinding? = null
    private val binding get() = _binding!!

    // THIS IS THE KEY CHANGE
    private val viewModel: PolicyViewModel by viewModels {
        // 1. Get the application context safely
        val applicationContext = requireActivity().applicationContext

        // 2. Create the dependency for the repository
        val stringProvider = StringProviderImpl(applicationContext)

        // 3. Create the repository with its dependency
        val repository = PolicyRepository(stringProvider)

        // 4. Provide the repository to the ViewModel's factory
        PolicyViewModelFactory(repository)
    }

    private lateinit var policyType: PolicyType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get policy type from arguments
        val policyTypeName = arguments?.getString(ARG_POLICY_TYPE)
        policyType = policyTypeName?.let {
            PolicyType.valueOf(it)
        } ?: PolicyType.PRIVACY_POLICY
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPolicyBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var bottomSheetBehavior: com.google.android.material.bottomsheet.BottomSheetBehavior<View>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()

        // Load the policy data
        viewModel.loadPolicy(policyType)

        // Setup bottom sheet behavior after dialog is shown
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                bottomSheetBehavior?.apply {
                    // Set initial state to collapsed with reasonable peek height
                    peekHeight = (350 * resources.displayMetrics.density).toInt()
                    state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                    skipCollapsed = false
                    isHideable = true
                }
            }
        }
    }

    private fun setupUI() {
        // Set initial title
        binding.policyType.text = policyType.title

        // Close button click
        binding.closeButton.setOnClickListener {
            dismissWithAnimation()
        }

        // Retry button click
        binding.retryButton.setOnClickListener {
            viewModel.retry()
        }

        // Handle back press
        dialog?.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                dismissWithAnimation()
                true
            } else {
                false
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (_binding != null) {
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: UiState<PolicyData>) {
        // Safety check to ensure binding is not null
        if (_binding == null) return

        when (state) {
            is UiState.Loading -> showLoadingState()
            is UiState.Success -> showSuccessState(state.data)
            is UiState.Error -> showErrorState(state.message)
        }
    }

    private fun showLoadingState() {
        with(binding) {
            loadingLayout.isVisible = true
            contentScrollView.isVisible = false
            errorLayout.isVisible = false
        }

        // Add subtle rotation animation to progress bar
        binding.loadingLayout.findViewById<View>(android.R.id.progress)?.let { progressBar ->
            ObjectAnimator.ofFloat(progressBar, "rotation", 0f, 360f).apply {
                duration = 2000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun showSuccessState(policyData: PolicyData) {
        with(binding) {
            loadingLayout.isVisible = false
            errorLayout.isVisible = false
            contentScrollView.isVisible = true

            // Update content
            policyType.text = policyData.title
            policyContent.text = Html.fromHtml(policyData.content, Html.FROM_HTML_MODE_LEGACY)
            lastUpdated.text = policyData.lastUpdated
        }

        // Smooth fade-in animation for content
        binding.contentScrollView.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { expandToFullHeight() }
                .start()
        }
    }

    private fun showErrorState(errorMessage: String) {
        with(binding) {
            loadingLayout.isVisible = false
            contentScrollView.isVisible = false
            errorLayout.isVisible = true
            errorMessageText.text = errorMessage
        }

        // Shake animation for error state
        binding.errorLayout.apply {
            translationX = 0f
            animate()
                .translationX(10f)
                .setDuration(50)
                .withEndAction {
                    animate()
                        .translationX(-10f)
                        .setDuration(50)
                        .withEndAction {
                            animate()
                                .translationX(0f)
                                .setDuration(50)
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun expandToFullHeight() {
        bottomSheetBehavior?.let { behavior ->
            // Smoothly expand to show full content
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun dismissWithAnimation() {
        bottomSheetBehavior?.let { behavior ->
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
        } ?: dismiss()
    }

    override fun getTheme(): Int {
        return R.style.CustomBottomSheetDialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Don't call clearState here as it can cause the crash
        // The ViewModel will be cleared when the fragment is destroyed
    }
}

fun androidx.fragment.app.FragmentActivity.showPolicyBottomSheet(policyType: PolicyType) {
    val bottomSheet = PolicyBottomSheetFragment.newInstance(policyType)
    bottomSheet.show(supportFragmentManager, "PolicyBottomSheet")
}