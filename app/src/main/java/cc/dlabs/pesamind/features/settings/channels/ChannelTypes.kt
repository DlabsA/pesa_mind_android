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

