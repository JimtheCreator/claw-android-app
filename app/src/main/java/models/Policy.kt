package models

// PolicyType.kt
enum class PolicyType(val title: String) {
    PRIVACY_POLICY("Privacy Policy"),
    BILLING_POLICY("Billing Policy"),
    TERMS_OF_SERVICE("Terms of Service")
}

// PolicyData.kt
data class PolicyData(
    val type: PolicyType,
    val title: String,
    val content: String,
    val lastUpdated: String
)

// UiState.kt
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val exception: Throwable? = null) : UiState<Nothing>()
}