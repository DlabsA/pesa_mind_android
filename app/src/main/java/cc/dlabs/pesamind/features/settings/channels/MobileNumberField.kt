package cc.dlabs.pesamind.features.settings.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────
data class CountryCode(
    val name: String,
    val code: String,    // e.g. "+256"
    val flag: String,    // emoji flag
    val digitCount: Int  // expected local digits after country code
)

val COUNTRY_CODES = listOf(
    CountryCode("Uganda",       "+256", "🇺🇬", 9),
    CountryCode("Kenya",        "+254", "🇰🇪", 9),
    CountryCode("Tanzania",     "+255", "🇹🇿", 9),
    CountryCode("Rwanda",       "+250", "🇷🇼", 9),
    CountryCode("South Africa", "+27",  "🇿🇦", 9),
)

// ─────────────────────────────────────────────
//  Visual transformer — formats as: 07X XXX XXXX
// ─────────────────────────────────────────────
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(10)
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 3 || i == 6) append(' ')
                append(c)
            }
        }
        // Map offsets between original and formatted text
        val offsetMap = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceAtMost(digits.length)
                return clamped + when {
                    clamped > 6 -> 2
                    clamped > 3 -> 1
                    else        -> 0
                }
            }
            override fun transformedToOriginal(offset: Int): Int {
                val spaces = when {
                    offset > 8 -> 2
                    offset > 4 -> 1
                    else       -> 0
                }
                return (offset - spaces).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMap)
    }
}

// ─────────────────────────────────────────────
//  Main composable
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMoneyNumberField(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedCountry: CountryCode = COUNTRY_CODES[0],   // default Uganda
    onCountryChange: (CountryCode) -> Unit = {}
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Validation: true only when digit count matches expected for selected country
    val isValid = phoneNumber.filter { it.isDigit() }.length == selectedCountry.digitCount
    val showError = phoneNumber.isNotEmpty() && !isValid

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Country code dropdown ──────────────────────
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "${selectedCountry.flag} ${selectedCountry.code}",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select country code"
                        )
                    },
                    modifier = Modifier
                        .width(130.dp)
                        .menuAnchor(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    COUNTRY_CODES.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text("${country.flag}  ${country.name}  (${country.code})")
                            },
                            onClick = {
                                onCountryChange(country)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ── Phone number input ─────────────────────────
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { raw ->
                    // Only allow digits, cap at expected length
                    val digits = raw.filter { it.isDigit() }
                        .take(selectedCountry.digitCount)
                    onPhoneNumberChange(digits)
                },
                label = { Text("Mobile Number") },
                placeholder = { Text("7X XXX XXXX") },
                visualTransformation = PhoneNumberVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                isError = showError,
                supportingText = {
                    when {
                        showError -> Text(
                            "Enter a valid ${selectedCountry.digitCount}-digit number",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                        isValid -> Text(
                            "✓  ${selectedCountry.code} ${phoneNumber.take(3)} " +
                                    "${phoneNumber.substring(3, 6)} ${phoneNumber.drop(6)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}