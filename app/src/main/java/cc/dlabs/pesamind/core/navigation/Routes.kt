package cc.dlabs.pesamind.core.navigation

sealed class Routes(val route: String) {
    // Auth
    object Splash : Routes("splash")
    object Login : Routes("login")
    object Register : Routes("register")
    object PinUnlock : Routes("pin_unlock")
    object PatternUnlock : Routes("pattern_unlock")

    // Main (bottom nav)
    object Dashboard : Routes("dashboard")
    object Analytics : Routes("analytics")
    object Tools : Routes("tools")       // budget
    object Settings : Routes("settings")
    object Home : Routes("home")

    // Sub-screens
    object AddTransaction : Routes("add_transaction")
    object Accounts : Routes("accounts")
    object BudgetDetail : Routes("budget_detail")
}