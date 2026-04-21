package cc.dlabs.pesamind.features.tools

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import cc.dlabs.pesamind.core.theme.PesaMindTheme
import cc.dlabs.pesamind.features.home.MainScreen
import androidx.compose.foundation.shape.CircleShape

// --- THEME COLORS (You can later move these to your Theme.kt) ---
val PesaTeal = Color(0xFF0D9B8B)
val PesaLightGray = Color(0xFFF3F3F3)
val PesaDarkBackground = Color(0xFFE8E9E8)

@Composable
fun BudgetScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PesaDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        YearlyBudgetSummaryCard()
        SetBudgetReminderCard()
        CurrentMonthBudgetCard()

        Button(
            onClick = { /* TODO: Link to details */ },
            colors = ButtonDefaults.buttonColors(containerColor = PesaTeal),
            modifier = Modifier.fillMaxWidth().height(55.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("More Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom nav
    }
}

@Composable
fun BudgetStatRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 15.sp)
        Text(text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 17.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)) {
                append(value)
            }
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 11.sp)) {
                append("UGX")
            }
        })
    }
}

@Composable
fun YearlyBudgetSummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PesaLightGray),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Yearly Budget", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(12.dp))
            BudgetStatRow("Expenditure", "5,000,000", true)
            BudgetStatRow("Income", "8,000,000", true)
            BudgetStatRow("Savings", "3,000,000", true)
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = PesaTeal),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("More Details", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SetBudgetReminderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Celebration, null, tint = PesaTeal, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Set September Budget", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Due 25th Nov", color = Color.Gray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = PesaTeal)
        }
    }
}

@Composable
fun CurrentMonthBudgetCard() {
    // Use a Box to allow the Bulb to "float" over the top edge of the Card
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp), // Pushes the card down so the bulb has space
            colors = CardDefaults.cardColors(containerColor = PesaLightGray),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 50.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "August Budget",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)
                )

                Surface(
                    color = Color(0xFFF8D7DA),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Transactions Status: Deficit",
                        color = Color(0xFF721C24),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp
                    )
                }

                Text(text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 38.sp, fontWeight = FontWeight.Black)) { append("200,000") }
                    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 14.sp, color = Color.Gray)) { append("UGX") }
                })

                BudgetStatRow("Income", "100,000")
                BudgetStatRow("Expenditure", "300,000")
                BudgetStatRow("Total", "- 200,000")
            }
        }

        // The Floating Bulb
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Color(0xFFFFD700), // Yellow Gold
                modifier = Modifier.padding(15.dp).size(45.dp)
            )
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun FullAppPreview() {
    val mockNav = rememberNavController()
    PesaMindTheme {
        MainScreen(rootNav = mockNav)
    }
}