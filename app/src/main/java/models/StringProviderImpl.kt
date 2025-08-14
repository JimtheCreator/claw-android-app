package models

import android.content.Context
import androidx.annotation.StringRes
import interfaces.StringProvider

class StringProviderImpl(private val context: Context) : StringProvider {
    override fun getString(@StringRes resId: Int): String {
        return context.getString(resId)
    }
}