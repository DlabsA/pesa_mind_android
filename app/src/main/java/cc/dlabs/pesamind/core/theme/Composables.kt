package cc.dlabs.pesamind.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// ============================================================================
// TRANSACTION TYPE ENUM
// ============================================================================
enum class TransactionType {
    INCOME, EXPENSE, SAVINGS
}

// ============================================================================
// PRIMARY BUTTON
// ============================================================================
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(9999.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

// ============================================================================
// SECONDARY BUTTON
// ============================================================================
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(9999.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================================
// CATEGORY BADGE (Income/Expense/Savings)
// ============================================================================
@Composable
fun CategoryBadge(
    type: TransactionType,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val (bgColor, fgColor, label) = when (type) {
        TransactionType.INCOME -> Triple(
            if (isDark) DarkColors.IncomeBg else LightColors.IncomeBg,
            if (isDark) DarkColors.IncomeFg else LightColors.IncomeFg,
            "↑ Income"
        )
        TransactionType.EXPENSE -> Triple(
            if (isDark) DarkColors.ExpenseBg else LightColors.ExpenseBg,
            if (isDark) DarkColors.ExpenseFg else LightColors.ExpenseFg,
            "↓ Expense"
        )
        TransactionType.SAVINGS -> Triple(
            if (isDark) DarkColors.SavingsBg else LightColors.SavingsBg,
            if (isDark) DarkColors.SavingsFg else LightColors.SavingsFg,
            "→ Savings"
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(9999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor
        )
    }
}

// ============================================================================
// TRANSACTION AMOUNT (uses monospace)
// ============================================================================
@Composable
fun CurrencyAmount(
    amount: String,
    style: TextStyle = AmountMedium,
    type: TransactionType = TransactionType.EXPENSE,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val color = when (type) {
        TransactionType.INCOME -> if (isDark) DarkColors.Income else LightColors.Income
        TransactionType.EXPENSE -> if (isDark) DarkColors.Expense else LightColors.Expense
        TransactionType.SAVINGS -> if (isDark) DarkColors.Savings else LightColors.Savings
    }

    Text(
        text = amount,
        style = style,
        color = color,
        modifier = modifier
    )
}

// ============================================================================
// BALANCE CARD (Hero)
// ============================================================================
@Composable
fun BalanceCard(
    balance: String,
    income: String,
    expense: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
        }

        CurrencyAmount(
            amount = balance,
            style = AmountXLarge,
            type = TransactionType.SAVINGS,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(top = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Income block
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = income,
                        style = AmountSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // Expense block
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
                    .padding(start = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = expense,
                        style = AmountSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

// ============================================================================
// TRANSACTION ROW
// ============================================================================
@Composable
fun TransactionRow(
    merchant: String,
    category: String,
    amount: String,
    type: TransactionType,
    timestamp: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Container
            Box(
                modifier = Modifier
                    .background(
                        color = when (type) {
                            TransactionType.INCOME -> {
                                if (isSystemInDarkTheme()) {
                                    DarkColors.Income.copy(alpha = 0.15f)
                                } else {
                                    LightColors.Income.copy(alpha = 0.15f)
                                }
                            }
                            TransactionType.EXPENSE -> {
                                if (isSystemInDarkTheme()) {
                                    DarkColors.Expense.copy(alpha = 0.15f)
                                } else {
                                    LightColors.Expense.copy(alpha = 0.15f)
                                }
                            }
                            TransactionType.SAVINGS -> {
                                if (isSystemInDarkTheme()) {
                                    DarkColors.Savings.copy(alpha = 0.15f)
                                } else {
                                    LightColors.Savings.copy(alpha = 0.15f)
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (type) {
                        TransactionType.INCOME -> "↑"
                        TransactionType.EXPENSE -> "↓"
                        TransactionType.SAVINGS -> "→"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = when (type) {
                        TransactionType.INCOME -> if (isSystemInDarkTheme()) DarkColors.Income else LightColors.Income
                        TransactionType.EXPENSE -> if (isSystemInDarkTheme()) DarkColors.Expense else LightColors.Expense
                        TransactionType.SAVINGS -> if (isSystemInDarkTheme()) DarkColors.Savings else LightColors.Savings
                    }
                )
            }

            // Merchant details
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = merchant,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$category · $timestamp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Amount + Badge
            Box(
                modifier = Modifier.padding(start = 12.dp)
            ) {
                CurrencyAmount(
                    amount = amount,
                    type = type,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                CategoryBadge(
                    type = type,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
fun PesaMindTextField(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}







