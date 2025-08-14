package interfaces

import androidx.annotation.StringRes

interface StringProvider {
    fun getString(@StringRes resId: Int): String
}