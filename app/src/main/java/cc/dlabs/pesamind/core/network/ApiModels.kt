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
    val status: Boolean = true,
    @SerializedName("channel_desc")
    val channelDesc: String = "",
    // Local-only field: SMS notification flag (not sent to backend)
    @Transient
    val smsNotificationEnabled: Boolean = true
)

data class CreateChannelRequest(
    val name: String,
    @SerializedName("channel_type")
    val channelType: String,
    val description: String,
    @SerializedName("channel_desc")
    val channelDesc: String,
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

// SMS Message Model
data class SMSMessage(
    val id: String = "",
    @SerializedName("sender_id")
    val senderId: String = "",
    @SerializedName("sender_name")
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("channel_id")
    val channelId: String = ""
)

