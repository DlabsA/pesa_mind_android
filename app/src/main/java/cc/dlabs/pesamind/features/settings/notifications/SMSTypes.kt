package cc.dlabs.pesamind.features.settings.notifications

object MessageSender {
    const val MTNMobMoney = "MTNMobMoney"
    const val airtelmoney = "airtelmoney"
    const val stanbicbank = "stanbicbank"
    const val centenary = "centenary"

    val valid = listOf(MTNMobMoney, airtelmoney, stanbicbank,centenary)

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }
}