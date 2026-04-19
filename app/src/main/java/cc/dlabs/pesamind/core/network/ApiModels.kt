package cc.dlabs.pesamind.core.network.models

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val username: String = "",
    val email: String = "",
    val password: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String = ""
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    val error: String? = null,
    val profile: AuthProfile? = null
)

data class AuthProfile(
    val id: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    val username: String? = null,
    val type: String? = null,
    val balance: Double? = null
)

data class AuthRegisterResponse(
    val id: String? = null,
    val email: String? = null,
    val error: String? = null,
)

data class Account(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val type: String = "",
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

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null
)

data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String,
    val confirm_password: String
)

data class UserResponse(
    val id: String,
    @SerializedName("name")
    val username: String,
    val email: String,
    val balance: Double,
    val type: String
)

data class ChannelDetails(
    val id: String = "",
    @SerializedName("user_id")
    val userId: String = "",
    val name: String = "",
    @SerializedName("channel_type")
    val channelType: String = "",
    val description: String = "",
    @SerializedName("status")
    val status: Boolean = false
)

data class CreateChannelRequest(
    val name: String,
    @SerializedName("channel_type")
    val channelType: String,
    val description: String,
    val status: Boolean
)

data class UpdateChannelRequest(
    val name: String,
    val description: String,
    val status: Boolean
)

data class ApiMessageResponse(
    val message: String? = null,
    val error: String? = null
)
