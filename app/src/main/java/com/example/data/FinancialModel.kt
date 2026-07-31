package com.example.data

data class GrowthScenario(
    val name: String,
    val registeredUsers: Int,
    val verifiedUsers: Int,
    val activeUsers: Int,
    val avgMonthlyTxVolume: Double, // GTV per active user in USD
    val avgCommissionRate: Double, // combined margin (fees + spread)
    val monthlySubscriptionRevenue: Double,
    val merchantRevenue: Double,
    val description: String
) {
    val monthlyGtv: Double = activeUsers * avgMonthlyTxVolume
    val monthlyProductRevenue: Double = monthlyGtv * avgCommissionRate
    val totalMonthlyGrossRevenue: Double = monthlyProductRevenue + monthlySubscriptionRevenue + merchantRevenue
}

object FinancialProjections {
    const val SIX_MONTH_TARGET = 3000000.0 // $3M
    const val REQUIRED_MONTHLY_RUN_RATE = 500000.0 // $500,000 average

    val conservativeScenario = GrowthScenario(
        name = "Conservative",
        registeredUsers = 120000,
        verifiedUsers = 80000,
        activeUsers = 45000,
        avgMonthlyTxVolume = 150.0,
        avgCommissionRate = 0.018, // 1.8% average margin
        monthlySubscriptionRevenue = 40000.0,
        merchantRevenue = 15000.0,
        description = "Lower viral growth and steady transaction volume. Margin remains standard but trading volumes are modest."
    )

    val baseScenario = GrowthScenario(
        name = "Base (Realistic)",
        registeredUsers = 350000,
        verifiedUsers = 250000,
        activeUsers = 180000,
        avgMonthlyTxVolume = 200.0,
        avgCommissionRate = 0.020, // 2.0% average margin
        monthlySubscriptionRevenue = 120000.0,
        merchantRevenue = 60000.0,
        description = "Strong product-market fit with high retention. Balanced revenue from crypto exchange spreads, gift cards, and subscriptions."
    )

    val aggressiveScenario = GrowthScenario(
        name = "Aggressive",
        registeredUsers = 800000,
        verifiedUsers = 600000,
        activeUsers = 450000,
        avgMonthlyTxVolume = 280.0,
        avgCommissionRate = 0.022, // 2.2% margin (incl. instant settlement & virtual cards)
        monthlySubscriptionRevenue = 350000.0,
        merchantRevenue = 180000.0,
        description = "Hyper-growth fueled by campus ambassadors, viral referral bonuses, strategic merchant processing, and stablecoin trading corridors."
    )

    fun getScenarioByName(name: String): GrowthScenario {
        return when (name) {
            "Conservative" -> conservativeScenario
            "Aggressive" -> aggressiveScenario
            else -> baseScenario
        }
    }

    // Returns a status string: Above Target, On Target, or Below Target
    fun getRunRateAlert(actualMonthlyRevenue: Double): RunRateStatus {
        val percentageOfRequired = (actualMonthlyRevenue / REQUIRED_MONTHLY_RUN_RATE) * 100
        return when {
            percentageOfRequired >= 110.0 -> RunRateStatus.ABOVE_TARGET
            percentageOfRequired >= 95.0 -> RunRateStatus.ON_TARGET
            else -> RunRateStatus.BELOW_TARGET
        }
    }
}

enum class RunRateStatus(val label: String, val colorHex: String) {
    ABOVE_TARGET("Above Target", "#4CAF50"), // Green
    ON_TARGET("On Target", "#FFC107"), // Amber
    BELOW_TARGET("Below Target", "#F44336") // Red
}
