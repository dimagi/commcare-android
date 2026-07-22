package org.commcare.connect

import java.util.Currency
import java.util.Locale

object ConnectMoneyUtils {
    @JvmStatic
    fun moneyStringWithSymbol(
        currency: String?,
        value: Int,
    ): String {
        if (currency.isNullOrEmpty()) {
            return value.toString()
        }
        val code = currency.uppercase(Locale.ROOT)
        val symbol =
            try {
                Currency.getInstance(code).symbol
            } catch (_: IllegalArgumentException) {
                code
            }
        return if (symbol == code) {
            String.format(Locale.getDefault(), "%d %s", value, symbol)
        } else {
            String.format(Locale.getDefault(), "%s%d", symbol, value)
        }
    }
}
