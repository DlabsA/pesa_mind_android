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
    const val ABSA_BANK = "Absa Bank"
    const val BANK_OF_AFRICA = "Bank of Africa"
    const val BANK_OF_BARODA = "Bank of Baroda"
    const val BANK_OF_INDIA = "Bank of India"
    const val CAIRO_BANK = "Cairo Bank"
    const val CENTENARY_BANK = "Centenary Bank"
    const val CITIBANK = "Citibank"
    const val DFCU_BANK = "DFCU Bank"
    const val DIAMOND_TRUST_BANK = "Diamond Trust Bank"
    const val ECOBANK = "Ecobank"
    const val EQUITY_BANK = "Equity Bank"
    const val EXIM_BANK = "Exim Bank"
    const val FINANCE_TRUST_BANK = "Finance Trust Bank"
    const val GTBANK = "GTBank"
    const val HOUSING_FINANCE_BANK = "Housing Finance Bank"
    const val I_M_BANK = "I&M Bank"
    const val KCB_BANK = "KCB Bank"
    const val NCBA_BANK = "NCBA Bank"
    const val OPPORTUNITY_BANK = "Opportunity Bank"
    const val PEARL_BANK = "Pearl Bank"
    const val POSTBANK_UGANDA = "PostBank Uganda"
    const val PRIDE_BANK = "Pride Bank"
    const val SALAAM_BANK = "Salaam Bank"
    const val STANBIC_BANK = "Stanbic Bank"
    const val STANDARD_CHARTERED_BANK = "Standard Chartered Bank"
    const val TROPICAL_BANK = "Tropical Bank"
    const val UBA_UGANDA = "UBA Uganda"

    val valid = listOf(
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

