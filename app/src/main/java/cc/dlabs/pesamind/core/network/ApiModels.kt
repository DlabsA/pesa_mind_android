package cc.dlabs.pesamind.core.network

data class RegisterRequest(
    val name: String = "",
    val email: String = "",
    val password: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

data class RefreshRequest(
    val refreshToken: String = ""
)

data class AuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val message: String? = null
)

data class Account(
    val id: String = "",
    val name: String = "",
    val balance: Double = 0.0
)

data class TransactionRequest(
    val accountId: String = "",
    val amount: Double = 0.0,
    val description: String = ""
)

data class Transaction(
    val id: String = "",
    val accountId: String = "",
    val amount: Double = 0.0,
    val description: String = ""
)

data class AnalyticsResponse(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0
)

data class Budget(
    val id: String = "",
    val category: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0
)
