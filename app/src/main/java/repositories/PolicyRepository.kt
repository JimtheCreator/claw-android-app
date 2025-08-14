package repositories

// PolicyRepository.kt
import com.claw.ai.R
import interfaces.StringProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import models.PolicyData
import models.PolicyType
import models.UiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.util.*

class PolicyRepository(private val stringProvider: StringProvider) {

    fun getPolicyData(type: PolicyType): Flow<UiState<PolicyData>> = flow {
        emit(UiState.Loading)
        try {
            delay(Random.nextLong(1500, 3000)) // Simulate network delay
            if (Random.nextFloat() < 0.1f) {
                throw Exception("Network error occurred")
            }
            val policyData = generatePolicyContent(type)
            emit(UiState.Success(policyData))
        } catch (e: Exception) {
            emit(UiState.Error("Failed to load ${type.title.lowercase()}", e))
        }
    }

    private fun generatePolicyContent(type: PolicyType): PolicyData {
        val contentResId = when (type) {
            PolicyType.PRIVACY_POLICY -> R.string.privacy_policy
            PolicyType.BILLING_POLICY -> R.string.billing_and_payment_policy
            PolicyType.TERMS_OF_SERVICE -> R.string.terms_of_service
        }
        val content = stringProvider.getString(contentResId)
        val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val lastUpdated = "Last updated: ${formatter.format(Date())}"

        return PolicyData(
            type = type,
            title = type.title,
            content = content,
            lastUpdated = lastUpdated
        )
    }
}