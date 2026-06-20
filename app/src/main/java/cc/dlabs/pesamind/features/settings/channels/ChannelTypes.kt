package cc.dlabs.pesamind.features.settings.channels

object ChannelTypes {
    const val CASH = "Cash"
    const val MOBILE_MONEY = "MobileMoney"
    const val BANK = "Bank"

    val valid = listOf(CASH, MOBILE_MONEY, BANK)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }
}

object ChannelDescMobileMoney {
    const val AIRTELMONEY = "Airtel Money"
    const val MTNMOBILEMONEY = "MTN Mobile Money"

    val valid = listOf(AIRTELMONEY, MTNMOBILEMONEY)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }
}

object ChannelDescBank {
    const val DFCU = "DFCU"
    const val EQUITYBANK = "Equity Bank"
    const val STANBICBANK = "Stanbic Bank"

    val valid = listOf(DFCU, EQUITYBANK, STANBICBANK)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }
}

