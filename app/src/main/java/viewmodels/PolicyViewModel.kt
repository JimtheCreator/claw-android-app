package viewmodels

// PolicyViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.PolicyData
import models.PolicyType
import models.UiState
import repositories.PolicyRepository

class PolicyViewModel(private val repository: PolicyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<PolicyData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PolicyData>> = _uiState.asStateFlow()

    private var currentPolicyType: PolicyType? = null

    fun loadPolicy(policyType: PolicyType) {
        if (currentPolicyType == policyType && _uiState.value is UiState.Success) {
            // Don't reload if same policy and already loaded
            return
        }

        currentPolicyType = policyType

        viewModelScope.launch {
            repository.getPolicyData(policyType).collect { state ->
                _uiState.value = state
            }
        }
    }

    fun retry() {
        currentPolicyType?.let { type ->
            loadPolicy(type)
        }
    }

    fun clearState() {
        _uiState.value = UiState.Loading
        currentPolicyType = null
    }
}

class PolicyViewModelFactory(
    private val repository: PolicyRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PolicyViewModel::class.java)) {
            return PolicyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}