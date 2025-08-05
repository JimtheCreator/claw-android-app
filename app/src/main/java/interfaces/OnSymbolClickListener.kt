package interfaces

import models.Symbol

interface OnSymbolClickListener {
    fun onSymbolClicked(symbol: Symbol?)
}