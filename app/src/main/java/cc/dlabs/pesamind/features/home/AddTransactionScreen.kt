package cc.dlabs.pesamind.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.dlabs.pesamind.core.theme.*
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.settings.channels.ChannelViewModel
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.navigation.NavHostController
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

// ─────────────────────────────────────────────────────────────────────────────
//  Transaction type constants — align with TransactionTypes.valid in ViewModel
// ─────────────────────────────────────────────────────────────────────────────
public const val TYPE_INCOME  = "income"
public const val TYPE_EXPENSE = "expense"
public const val TYPE_SAVING = "saving"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController,
    viewModel: TransactionViewModel = viewModel(),
    channelViewModel: ChannelViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    // ── Form state ────────────────────────────────────────────────────────────
    var channelId   by remember { mutableStateOf("") }
    var channelDropdownExpanded by remember { mutableStateOf(false) }
    var amountText  by remember { mutableStateOf("") }
    var note        by remember { mutableStateOf("") }
    var txType      by remember { mutableStateOf(TYPE_EXPENSE) }

    // ── Inline validation ─────────────────────────────────────────────────────
    val amountError  = amountText.isNotEmpty() &&
            (amountText.toDoubleOrNull() == null || amountText.toDouble() <= 0)
    val channelError = channelId.isNotEmpty() && channelId.isBlank()

    // ── Channel list from ChannelViewModel ──
    val channelState by channelViewModel.state.collectAsStateWithLifecycle()
    val channelList = channelState.channels

    // ── React to ViewModel state changes ──────────────────────────────────────
    LaunchedEffect(state.message) {
        if (!state.message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = state.message!!,
                duration = SnackbarDuration.Short
            )
            navController.popBackStack()
        }
    }

    LaunchedEffect(state.error) {
        if (!state.error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = state.error!!,
                duration = SnackbarDuration.Short
            )
        }
    }

    // ── Accent colour follows selected type ──────────────────────────────────
    val accentColor by animateColorAsState(
        targetValue = when (txType) {
            TYPE_INCOME -> if (isDark) DarkColors.Income else LightColors.Income
            TYPE_SAVING -> if (isDark) DarkColors.Savings else LightColors.Savings
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        },
        animationSpec = tween(300),
        label = "accentColor"
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = accentColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero header ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.8f))
                        )
                    )
                    .padding(top = 24.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                // Back button
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Amount display — centre stage
                    Text(
                        text = "UGX",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 2.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (amountText.isEmpty()) {
                            "0"
                        } else {
                                val number = amountText.toBigInteger()
                                NumberFormat.getNumberInstance(LocalLocale.current.platformLocale)
                                    .apply<NumberFormat> {
                                        this.minimumFractionDigits = 0
                                        this.maximumFractionDigits = 0
                                    }.format(number)
                        },
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1).sp
                    )
                }
            }

            // ── Card that overlaps the hero ───────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-20.dp))
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    // ── Income / Expense toggle ───────────────────────────────
                    TransactionTypeToggle(
                        selected = txType,
                        onSelect = { txType = it },
                        accentColor = accentColor
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )

                    // ── Amount field ──────────────────────────────────────────
                    LabeledField(label = "Amount") {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                // Allow only valid decimal numbers
                                if (raw.isEmpty() || raw.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?\$"))) {
                                    amountText = raw
                                }
                            },
                            placeholder = { Text("0.00") },
                            prefix = { Text("UGX  ", fontWeight = FontWeight.SemiBold) },
                            isError = amountError,
                            supportingText = if (amountError) {
                                { Text("Enter a valid amount greater than zero") }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                cursorColor = accentColor,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // ── Channel ID dropdown ──────────────────────────────────
                    LabeledField(label = "Channel") {
                        ExposedDropdownMenuBox(
                            expanded = channelDropdownExpanded,
                            onExpandedChange = { channelDropdownExpanded = !channelDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = channelList.find { it.id == channelId }?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select a channel") },
                                isError = channelError,
                                supportingText = if (channelError) {
                                    { Text("Channel ID cannot be blank") }
                                } else null,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    cursorColor = accentColor,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = channelDropdownExpanded,
                                onDismissRequest = { channelDropdownExpanded = false }
                            ) {
                                channelList.forEach { channel ->
                                    DropdownMenuItem(
                                        text = { Text(channel.name) },
                                        onClick = {
                                            channelId = channel.id
                                            channelDropdownExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }

                    // ── Note field ────────────────────────────────────────────
                    LabeledField(label = "Note") {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= 200) note = it },
                            placeholder = { Text("What was this for?") },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(
                                    text = "${note.length}/200",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                cursorColor = accentColor,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Submit button ─────────────────────────────────────────────────
            val canSubmit = channelId.isNotBlank()
                    && amountText.toDoubleOrNull() != null
                    && amountText.toDouble() > 0
                    && note.isNotBlank()
                    && !state.isSaving

            PrimaryButton(
                text = if (txType == TYPE_INCOME) "Record Income" else if (txType == TYPE_SAVING) "Record Savings" else "Record Expense",
                onClick = {
                    viewModel.CreateTransaction(
                        channelID = channelId.trim(),
                        amount    = amountText.toDouble(),
                        type      = txType,
                        note      = note.trim()
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Income / Expense pill toggle
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TransactionTypeToggle(
    selected: String,
    onSelect: (String) -> Unit,
    accentColor: Color
) {
     Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(TYPE_EXPENSE to "Expense", TYPE_INCOME to "Income", TYPE_SAVING to "Saving").forEach { (value, label) ->
            val isSelected = selected == value
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) accentColor else Color.Transparent,
                animationSpec = tween(250),
                label = "toggleBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250),
                label = "toggleText"
            )
            val icon = if (value == TYPE_INCOME) Icons.Default.Add else Icons.Default.Remove

             Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(value) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utility: label + field slot
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LabeledField(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        content()
    }
}