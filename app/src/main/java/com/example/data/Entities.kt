package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserAccount(
    @PrimaryKey val id: String = "default_user",
    val username: String = "oghenekaro",
    val email: String = "oghenekarojoseph@gmail.com",
    val tier: String = "Free", // Free, Premium, Business, Enterprise
    val kycStatus: String = "Unverified", // Unverified, Pending Review, Tier 1 Verified, Tier 2 Verified, Fully Verified
    val points: Int = 120, // Loyalty/referral points
    val referralCode: String = "APEX9RXT",
    val baseCurrency: String = "NGN",
    // Multi-step onboarding and KYC details
    val kycStep: Int = 0, // 0 = Unstarted, 1 = Personal Details done, 2 = ID Upload done, 3 = Under Review/Completed
    val fullName: String = "",
    val bvn: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val docUploaded: Boolean = false,
    val docFileName: String = ""
)

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val currencyCode: String, // NGN, USD, GBP, EUR, BTC, USDT, ETH
    val currencyName: String,
    val currencySymbol: String,
    val balance: Double,
    val isCrypto: Boolean,
    val rateToNgn: Double // Exchange rate reference
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amount: Double,
    val currency: String,
    val type: String, // SEND, RECEIVE, BILL, EXCHANGE, GIFT_CARD, REWARD
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Completed", // Completed, Pending, Failed
    val category: String = "Uncategorized" // Food, Utilities, Entertainment, Investment, Shopping, Rewards, Uncategorized
)

@Entity(tableName = "gift_card_trades")
data class GiftCardTrade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String, // Steam, Amazon, iTunes, Google Play, etc.
    val faceValue: Double, // Face value in USD/EUR
    val currency: String = "USD",
    val rate: Double, // NGN rate per USD
    val payoutAmount: Double, // Amount to be paid in NGN
    val code: String, // E-code or physical code entered
    val status: String = "Pending", // Pending, Processing, Verified, Declined
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // USER, AI
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiLoanApplication(
    val id: String,
    val purpose: String,
    val amount: Double,
    val currency: String,
    val aiConfidenceScore: Double,
    val status: String,
    val note: String
)

data class EmbeddedPolicy(
    val id: String,
    val title: String,
    val terms: String,
    val status: String,
    val description: String
)

data class DeFiPool(
    val name: String,
    val apy: String,
    val stakedBalance: Double,
    val symbol: String,
    val protocol: String
)

data class CheckDeposit(
    val id: String,
    val checkNumber: String,
    val routingNumber: String,
    val accountNumber: String,
    val issuingBank: String,
    val amount: Double,
    val currency: String,
    val status: String, // "COMPLETED", "PROCESSING"
    val frontCaptured: Boolean,
    val backCaptured: Boolean,
    val dateSubmitted: String,
    val aiOcrConfidence: Double
)


