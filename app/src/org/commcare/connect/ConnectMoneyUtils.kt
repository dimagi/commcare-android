package org.commcare.connect

import java.util.Currency
import java.util.Locale

object ConnectMoneyUtils {
    @JvmStatic
    fun moneyStringWithSymbol(
        currency: String?,
        value: Int,
    ): String {
        val symbol =
            if (currency.isNullOrEmpty()) {
                ""
            } else {
                try {
                    Currency.getInstance(currency.uppercase(Locale.ROOT)).symbol
                } catch (_: IllegalArgumentException) {
                    currency
                }
            }
        return String.format(Locale.getDefault(), "%s%d", symbol, value)
    }
}
