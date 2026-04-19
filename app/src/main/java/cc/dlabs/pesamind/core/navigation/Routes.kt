package cc.dlabs.pesamind.core.navigation

sealed class Routes(val route: String) {
    // Auth
    object Splash : Routes("splash")
    object Login : Routes("login")
    object Register : Routes("register")
    object PinUnlock : Routes("pin_unlock")
    object PatternUnlock : Routes("pattern_unlock")
    object LockSetup : Routes("lock_setup")
    object PinSetup : Routes("pin_setup")
    object PatternSetup : Routes("pattern_setup")

    // Main (bottom nav)
    object Dashboard : Routes("dashboard")
    object Analytics : Routes("analytics")
    object Tools : Routes("tools")
    object Settings : Routes("settings")
    object Home : Routes("home")

    // Sub-screens
    object AddTransaction : Routes("add_transaction")
//    object Accounts : Routes("accounts")
//    object BudgetDetail : Routes("budget_detail")

    // Security in account settings
    object SecuritySettings : Routes("security_settings")
    object SetPin : Routes("set_pin")
    object SetPattern : Routes("set_pattern")

    //
    object AccountSettings : Routes("account_settings")
    object ChangePassword : Routes("change_password")
    object Channels : Routes("channels")
}