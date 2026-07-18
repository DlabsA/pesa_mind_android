package cc.dlabs.pesamind.features.settings.notifications

object MessageSender {
    // Mobile Money Senders
    const val MTN_MOB_MONEY = "MTNMobMoney"
    const val AIRTEL_MONEY = "airtelmoney"

    // Bank Senders
    const val ABSA_BANK = "absabank"
    const val BANK_OF_AFRICA = "bankofafrica"
    const val BANK_OF_BARODA = "bankofbaroda"
    const val BANK_OF_INDIA = "bankofindia"
    const val CAIRO_BANK = "cairobank"
    const val CENTENARY_BANK = "centenary"
    const val CITIBANK = "citibank"
    const val DFCU_BANK = "dfcubank"
    const val DIAMOND_TRUST_BANK = "diamondtrustbank"
    const val ECOBANK = "ecobank"
    const val EQUITY_BANK = "equitybank"
    const val EXIM_BANK = "eximbank"
    const val FINANCE_TRUST_BANK = "financetrustbank"
    const val GTBANK = "gtbank"
    const val HOUSING_FINANCE_BANK = "housingfinancebank"
    const val I_M_BANK = "imbank"
    const val KCB_BANK = "kcbbank"
    const val NCBA_BANK = "ncbabank"
    const val OPPORTUNITY_BANK = "opportunitybank"
    const val PEARL_BANK = "pearlbank"
    const val POSTBANK_UGANDA = "postbankuganda"
    const val PRIDE_BANK = "pridebank"
    const val SALAAM_BANK = "salaambank"
    const val STANBIC_BANK = "stanbicbank"
    const val STANDARD_CHARTERED_BANK = "standardcharteredbank"
    const val TROPICAL_BANK = "tropicalbank"
    const val UBA_UGANDA = "ubauganda"

    val valid = listOf(
        MTN_MOB_MONEY,
        AIRTEL_MONEY,
        ABSA_BANK,
        BANK_OF_AFRICA,
        BANK_OF_BARODA,
        BANK_OF_INDIA,
        CAIRO_BANK,
        CENTENARY_BANK,
        CITIBANK,
        DFCU_BANK,
        DIAMOND_TRUST_BANK,
        ECOBANK,
        EQUITY_BANK,
        EXIM_BANK,
        FINANCE_TRUST_BANK,
        GTBANK,
        HOUSING_FINANCE_BANK,
        I_M_BANK,
        KCB_BANK,
        NCBA_BANK,
        OPPORTUNITY_BANK,
        PEARL_BANK,
        POSTBANK_UGANDA,
        PRIDE_BANK,
        SALAAM_BANK,
        STANBIC_BANK,
        STANDARD_CHARTERED_BANK,
        TROPICAL_BANK,
        UBA_UGANDA
    )

    fun normalizeOrNull(raw: String): String? {
        return valid.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    }
}