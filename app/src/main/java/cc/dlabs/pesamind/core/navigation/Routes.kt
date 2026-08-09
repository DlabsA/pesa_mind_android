package cc.dlabs.pesamind.core.navigation

sealed class Routes(val route: String) {
    // Auth
    object Login : Routes("login")

    object Register : Routes("register")

    object PinUnlock : Routes("pin_unlock")

    object PatternUnlock : Routes("pattern_unlock")

    object LockSetup : Routes("lock_setup")

    object PinSetup : Routes("pin_setup")

    object PatternSetup : Routes("pattern_setup")

    // Post-signup channel onboarding (one-shot, skippable, batch-creates up to 4 channels)
    object ChannelOnboardingIntro : Routes("onboarding_intro")

    object OnboardingCash : Routes("onboarding_cash")

    object OnboardingMoMo : Routes("onboarding_momo")

    object OnboardingAirtel : Routes("onboarding_airtel")

    object OnboardingBank : Routes("onboarding_bank")

    object OnboardingReview : Routes("onboarding_review")

    // Main (bottom nav)
    object Dashboard : Routes("dashboard")

    object Analytics : Routes("analytics")

    object Tools : Routes("tools")

    object Settings : Routes("settings")

    object Home : Routes("home")

    // Sub-screens
    object AddTransaction : Routes("add_transaction?channelId={channelId}") {
        /** [channelId] pre-selects a channel (e.g. the "Add Transaction" pill on a channel
         * card) — omit for the plain, empty-picker entry point used elsewhere. */
        fun createRoute(channelId: String? = null): String =
            if (channelId.isNullOrBlank()) "add_transaction" else "add_transaction?channelId=$channelId"
    }
//    object Accounts : Routes("accounts")
//    object BudgetDetail : Routes("budget_detail")

    // Security in account settings
    object SecuritySettings : Routes("security_settings")

    object SetPin : Routes("set_pin")

    object SetPattern : Routes("set_pattern")

    //
    object AccountSettings : Routes("account_settings")

    object ChangePassword : Routes("change_password")

    object Upgrade : Routes("upgrade")

    object Channels : Routes("channels")

    object ChannelDetail : Routes("channel_detail/{channelId}") {
        fun createRoute(channelId: String) = "channel_detail/$channelId"
    }

    object TransactionList : Routes("transaction_list")

    object SetMonthlyBudget : Routes("set_monthly_budget/{month}/{year}") {
        fun createRoute(
            month: Int,
            year: Int,
        ) = "set_monthly_budget/$month/$year"
    }

    object SetYearlyBudget : Routes("set_yearly_budget")
}
