package com.zmd.charge.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.zmd.charge.R

class EndfieldHeaderPreference(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.pref_header
        isSelectable = false
    }
}
