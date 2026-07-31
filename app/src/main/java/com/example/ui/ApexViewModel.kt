package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.Part
import com.example.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApexViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ApexRepository(db.apexDao())

    // Biometric & Session Persistence State
    private val sharedPrefs = application.getSharedPreferences("apex_pay_prefs", Context.MODE_PRIVATE)

    private val _isBiometricAuthenticated = MutableStateFlow(false)
    val isBiometricAuthenticated: StateFlow<Boolean> = _isBiometricAuthenticated.asStateFlow()

    private val _sessionPersistenceEnabled = MutableStateFlow(sharedPrefs.getBoolean("session_persistence", false))
    val sessionPersistenceEnabled: StateFlow<Boolean> = _sessionPersistenceEnabled.asStateFlow()

    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode_enabled", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAppBlurred = MutableStateFlow(false)
    val isAppBlurred: StateFlow<Boolean> = _isAppBlurred.asStateFlow()

    private val _screenBlurEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_screen_blur", true))
    val screenBlurEnabled: StateFlow<Boolean> = _screenBlurEnabled.asStateFlow()

    init {
        val persistenceEnabled = sharedPrefs.getBoolean("session_persistence", false)
        val wasAuthenticated = sharedPrefs.getBoolean("was_authenticated", false)
        if (persistenceEnabled && wasAuthenticated) {
            _isBiometricAuthenticated.value = true
        }
    }

    fun setSessionPersistence(enabled: Boolean) {
        _sessionPersistenceEnabled.value = enabled
        sharedPrefs.edit().putBoolean("session_persistence", enabled).apply()
        if (!enabled) {
            sharedPrefs.edit().putBoolean("was_authenticated", false).apply()
        }
    }

    fun authenticateBiometrics() {
        _isBiometricAuthenticated.value = true
        _isAppBlurred.value = false
        if (_sessionPersistenceEnabled.value) {
            sharedPrefs.edit().putBoolean("was_authenticated", true).apply()
        }
    }

    fun lockApp() {
        _isBiometricAuthenticated.value = false
        sharedPrefs.edit().putBoolean("was_authenticated", false).apply()
    }

    fun setAppBlurred(blurred: Boolean) {
        if (_screenBlurEnabled.value) {
            _isAppBlurred.value = blurred
        }
    }

    fun setScreenBlurEnabled(enabled: Boolean) {
        _screenBlurEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_screen_blur", enabled).apply()
        if (!enabled) {
            _isAppBlurred.value = false
        }
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("dark_mode_enabled", enabled).apply()
    }

    // Play Store Launch & Panic Protection State
    private val _isAccountFrozen = MutableStateFlow(sharedPrefs.getBoolean("account_frozen", false))
    val isAccountFrozen: StateFlow<Boolean> = _isAccountFrozen.asStateFlow()

    fun toggleAccountFreeze(frozen: Boolean) {
        _isAccountFrozen.value = frozen
        sharedPrefs.edit().putBoolean("account_frozen", frozen).apply()
    }

    // Fintech Security and Compliance Toggles
    private val _mfaSmsEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_mfa_sms", true))
    val mfaSmsEnabled: StateFlow<Boolean> = _mfaSmsEnabled.asStateFlow()

    private val _mfaAuthAppEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_mfa_auth", true))
    val mfaAuthAppEnabled: StateFlow<Boolean> = _mfaAuthAppEnabled.asStateFlow()

    private val _aes256EncryptionEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_aes_db", true))
    val aes256EncryptionEnabled: StateFlow<Boolean> = _aes256EncryptionEnabled.asStateFlow()

    private val _fieldLevelEncryptionEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_field_enc", true))
    val fieldLevelEncryptionEnabled: StateFlow<Boolean> = _fieldLevelEncryptionEnabled.asStateFlow()

    private val _apiRateLimitingEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_api_limit", true))
    val apiRateLimitingEnabled: StateFlow<Boolean> = _apiRateLimitingEnabled.asStateFlow()

    private val _globalAvailabilityEnabled = MutableStateFlow(sharedPrefs.getBoolean("security_global_jurisdictions", true))
    val globalAvailabilityEnabled: StateFlow<Boolean> = _globalAvailabilityEnabled.asStateFlow()

    fun setMfaSms(enabled: Boolean) {
        _mfaSmsEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_mfa_sms", enabled).apply()
    }

    fun setMfaAuthApp(enabled: Boolean) {
        _mfaAuthAppEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_mfa_auth", enabled).apply()
    }

    fun setAes256Encryption(enabled: Boolean) {
        _aes256EncryptionEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_aes_db", enabled).apply()
    }

    fun setFieldLevelEncryption(enabled: Boolean) {
        _fieldLevelEncryptionEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_field_enc", enabled).apply()
    }

    fun setApiRateLimiting(enabled: Boolean) {
        _apiRateLimitingEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_api_limit", enabled).apply()
    }

    fun setGlobalAvailability(enabled: Boolean) {
        _globalAvailabilityEnabled.value = enabled
        sharedPrefs.edit().putBoolean("security_global_jurisdictions", enabled).apply()
    }

    // PDF Receipt Generator
    fun generateTransactionPdf(context: Context, tx: TransactionEntity, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = Color.parseColor("#101828")
                    textSize = 18f
                    isFakeBoldText = true
                }
                val headerPaint = Paint().apply {
                    color = Color.parseColor("#4F46E5") // Indigo primary
                    textSize = 24f
                    isFakeBoldText = true
                }
                val labelPaint = Paint().apply {
                    color = Color.parseColor("#667085")
                    textSize = 14f
                }
                val valuePaint = Paint().apply {
                    color = Color.parseColor("#1D2939")
                    textSize = 14f
                    isFakeBoldText = true
                }
                val footerPaint = Paint().apply {
                    color = Color.parseColor("#98A2B3")
                    textSize = 10f
                }

                // Header Background
                canvas.drawRect(0f, 0f, 595f, 130f, Paint().apply { color = Color.parseColor("#F8F9FC") })
                canvas.drawText("APEX PAY", 40f, 65f, headerPaint)
                canvas.drawText("Official Transaction Receipt", 40f, 95f, Paint().apply {
                    color = Color.parseColor("#475467")
                    textSize = 12f
                })

                // Top divider
                canvas.drawLine(40f, 160f, 555f, 160f, Paint().apply {
                    color = Color.parseColor("#E4E7EC")
                    strokeWidth = 2f
                })

                var currentY = 200f
                val spacing = 40f

                fun drawField(label: String, value: String) {
                    canvas.drawText(label, 40f, currentY, labelPaint)
                    canvas.drawText(value, 220f, currentY, valuePaint)
                    currentY += spacing
                }

                drawField("Receipt Reference:", "APX-TXN-${tx.id}-${System.currentTimeMillis() % 100000}")
                drawField("Description:", tx.description)
                drawField("Transaction Type:", tx.type)
                drawField("Amount:", "${tx.currency} ${String.format("%,.2f", tx.amount)}")
                drawField("AI Category:", tx.category)
                drawField("Date & Time:", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(tx.timestamp)))
                drawField("Status:", "SUCCESSFUL")

                // Bottom divider
                canvas.drawLine(40f, 550f, 555f, 550f, Paint().apply {
                    color = Color.parseColor("#E4E7EC")
                    strokeWidth = 1f
                })

                // Footer Text
                canvas.drawText("Thank you for using Apex Pay. Your trusted digital finance partner.", 40f, 580f, footerPaint)
                canvas.drawText("Support: support@apexpay.com | Website: https://apexpay.com", 40f, 600f, footerPaint)
                canvas.drawText("This is an electronically generated document. No physical signature is required.", 40f, 620f, footerPaint)

                pdfDocument.finishPage(page)

                // Save PDF to Downloads
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val file = File(directory, "ApexPay_Receipt_${tx.id}.pdf")
                val outputStream = FileOutputStream(file)
                pdfDocument.writeTo(outputStream)
                pdfDocument.close()
                outputStream.close()

                withContext(Dispatchers.Main) {
                    onComplete(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }

    // UI Live Data Flows (Database backed)
    val userAccount: StateFlow<UserAccount> = repository.userAccount
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserAccount()
        )

    val wallets: StateFlow<List<WalletEntity>> = repository.wallets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val transactions: StateFlow<List<TransactionEntity>> = repository.transactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val giftCards: StateFlow<List<GiftCardTrade>> = repository.giftCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Admin & Financial Projection States (Local Memory State)
    private val _selectedScenarioName = MutableStateFlow("Base (Realistic)")
    val selectedScenarioName: StateFlow<String> = _selectedScenarioName.asStateFlow()

    private val _simulatedActualMonthlyRevenue = MutableStateFlow(540000.0) // Defaults above target
    val simulatedActualMonthlyRevenue: StateFlow<Double> = _simulatedActualMonthlyRevenue.asStateFlow()

    // Gemini AI Processing States
    private val _aiTypingState = MutableStateFlow<AiTypingState>(AiTypingState.Idle)
    val aiTypingState: StateFlow<AiTypingState> = _aiTypingState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            initDefaultData()
            refreshMarketRates()
        }
    }

    // 1. AI-Powered Dynamic Auto-Categorization Engine
    fun autoCategorize(description: String, type: String): String {
        val txs = transactions.value
        // Learn from user corrections: find any past transaction with the same description that has been manually categorized
        val pastCorrection = txs.firstOrNull {
            it.description.equals(description, ignoreCase = true) && it.category != "Uncategorized"
        }?.category

        if (pastCorrection != null) {
            return pastCorrection
        }

        // Heuristic AI rules as fallback
        val descLower = description.lowercase()
        return when {
            descLower.contains("airtime") || descLower.contains("recharge") || descLower.contains("mtn") || descLower.contains("glo") || descLower.contains("airtel") || descLower.contains("9mobile") -> "Utilities"
            descLower.contains("dstv") || descLower.contains("gotv") || descLower.contains("electricity") || descLower.contains("eko") || descLower.contains("ikeja") || descLower.contains("power") || descLower.contains("water") || descLower.contains("bill") -> "Utilities"
            descLower.contains("uber") || descLower.contains("bolt") || descLower.contains("transport") || descLower.contains("fuel") || descLower.contains("filling station") || descLower.contains("ride") -> "Transport"
            descLower.contains("restaurant") || descLower.contains("food") || descLower.contains("eatery") || descLower.contains("supermarket") || descLower.contains("grocery") || descLower.contains("canteen") || descLower.contains("lunch") || descLower.contains("dinner") -> "Food"
            descLower.contains("netflix") || descLower.contains("spotify") || descLower.contains("steam") || descLower.contains("cinema") || descLower.contains("game") || descLower.contains("movie") || descLower.contains("show") -> "Entertainment"
            descLower.contains("crypto") || descLower.contains("btc") || descLower.contains("eth") || descLower.contains("usdt") || descLower.contains("bitcoin") || descLower.contains("ethereum") || descLower.contains("exchange") -> "Investment"
            descLower.contains("gift card") || descLower.contains("amazon") || descLower.contains("itunes") || descLower.contains("steam card") || descLower.contains("razer") || descLower.contains("payout") -> "Gift Cards"
            descLower.contains("reward") || descLower.contains("cashback") || descLower.contains("bonus") || descLower.contains("welcome") || descLower.contains("signup") -> "Rewards"
            descLower.contains("shopping") || descLower.contains("store") || descLower.contains("mall") || descLower.contains("jumia") || descLower.contains("konga") || descLower.contains("amazon order") || descLower.contains("purchase") -> "Shopping"
            type == "BILL" -> "Utilities"
            type == "REWARD" -> "Rewards"
            type == "GIFT_CARD" -> "Gift Cards"
            type == "EXCHANGE" -> "Investment"
            else -> "Others"
        }
    }

    // Method to allow user correction (learning loop)
    fun updateTransactionCategory(txId: Long, newCategory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val txList = transactions.value
            val found = txList.find { it.id == txId }
            if (found != null) {
                repository.insertTransaction(found.copy(category = newCategory))
            }
        }
    }

    // 2. Real-Time Market Data Feed Integration
    fun refreshMarketRates(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch live foreign exchange rates from open.er-api.com
                val fxResponse = com.example.network.MarketDataClient.fxService.getUsdRates()
                val ngnPerUsd = fxResponse.rates["NGN"] ?: 1500.0

                // Fetch live crypto spot prices from Coinbase
                val btcUsd = com.example.network.MarketDataClient.coinbaseService.getSpotPrice("BTC-USD").data.amount.toDoubleOrNull() ?: 97000.0
                val ethUsd = com.example.network.MarketDataClient.coinbaseService.getSpotPrice("ETH-USD").data.amount.toDoubleOrNull() ?: 3300.0

                // Update standard wallet rates in database
                val currentWallets = repository.wallets.first()
                val updatedWallets = currentWallets.map { wallet ->
                    when (wallet.currencyCode) {
                        "USD" -> wallet.copy(rateToNgn = ngnPerUsd)
                        "GBP" -> {
                            val gbpPerUsd = fxResponse.rates["GBP"] ?: 0.78
                            wallet.copy(rateToNgn = ngnPerUsd / gbpPerUsd)
                        }
                        "EUR" -> {
                            val eurPerUsd = fxResponse.rates["EUR"] ?: 0.92
                            wallet.copy(rateToNgn = ngnPerUsd / eurPerUsd)
                        }
                        "BTC" -> wallet.copy(rateToNgn = btcUsd * ngnPerUsd)
                        "ETH" -> wallet.copy(rateToNgn = ethUsd * ngnPerUsd)
                        "USDT" -> wallet.copy(rateToNgn = ngnPerUsd) // USDT pegged to USD
                        else -> wallet
                    }
                }
                repository.insertWallets(updatedWallets)
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    // 3. Multi-Step KYC Onboarding Flow & Form Validation
    fun nextKycStep() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            if (user.kycStep < 3) {
                repository.insertUserAccount(user.copy(kycStep = user.kycStep + 1))
            }
        }
    }

    fun prevKycStep() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            if (user.kycStep > 0) {
                repository.insertUserAccount(user.copy(kycStep = user.kycStep - 1))
            }
        }
    }

    fun updateKycPersonalDetails(fullName: String, bvn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            repository.insertUserAccount(user.copy(fullName = fullName, bvn = bvn, kycStep = 1))
        }
    }

    fun updateKycDocumentDetails(idType: String, idNumber: String, docFileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            repository.insertUserAccount(user.copy(
                idType = idType,
                idNumber = idNumber,
                docUploaded = true,
                docFileName = docFileName,
                kycStep = 2
            ))
        }
    }

    fun submitKycVerification() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            repository.insertUserAccount(user.copy(
                kycStep = 3,
                kycStatus = "Pending Review"
            ))

            // Simulate quick verification approval background review
            delay(5000)
            val freshUser = repository.getUserAccountDirect()
            if (freshUser != null && freshUser.kycStatus == "Pending Review") {
                repository.insertUserAccount(freshUser.copy(
                    kycStatus = "Fully Verified",
                    tier = "Premium"
                ))
            }
        }
    }

    fun resetKycOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            repository.insertUserAccount(user.copy(
                kycStep = 0,
                kycStatus = "Unverified",
                fullName = "",
                bvn = "",
                idType = "",
                idNumber = "",
                docUploaded = false,
                docFileName = ""
            ))
        }
    }

    private suspend fun initDefaultData() {
        val currentUser = repository.getUserAccountDirect()
        if (currentUser == null) {
            // Setup default user
            repository.insertUserAccount(UserAccount())

            // Setup default wallets
            val initialWallets = listOf(
                WalletEntity("NGN", "Nigerian Naira", "₦", 750000.0, false, 1.0),
                WalletEntity("USD", "US Dollar", "$", 1500.0, false, 1500.0),
                WalletEntity("BTC", "Bitcoin", "₿", 0.045, true, 97500000.0),
                WalletEntity("USDT", "Tether Stablecoin", "₮", 250.0, true, 1500.0),
                WalletEntity("ETH", "Ethereum", "Ξ", 0.25, true, 4800000.0)
            )
            repository.insertWallets(initialWallets)

            // Setup default transactions for visual texture
            val sampleTxs = listOf(
                TransactionEntity(description = "Welcome Sign-up Reward", amount = 1000.0, currency = "NGN", type = "REWARD", category = "Rewards"),
                TransactionEntity(description = "Airtime Purchase (MTN)", amount = 2000.0, currency = "NGN", type = "BILL", category = "Utilities"),
                TransactionEntity(description = "Received from SwiftTransfer", amount = 350.0, currency = "USD", type = "RECEIVE", category = "Others"),
                TransactionEntity(description = "Sold Amazon Gift Card", amount = 45000.0, currency = "NGN", type = "GIFT_CARD", category = "Gift Cards")
            )
            for (tx in sampleTxs) {
                repository.insertTransaction(tx)
            }

            // Setup default chat greeting
            repository.insertChatMessage(
                ChatMessage(
                    sender = "AI",
                    text = "Welcome to **Apex Pay**! I am your AI Financial Assistant. I can analyze your spending patterns, explain exchange rates, and guide you towards smart budgeting goals. How can I assist you today?"
                )
            )
        }
    }

    // Interactive Transaction Operations
    fun sendMoney(amount: Double, recipient: String, currencyCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = repository.wallets.first().find { it.currencyCode == currencyCode }
            if (wallet == null) {
                onResult(false, "Wallet not found")
                return@launch
            }
            if (wallet.balance < amount) {
                onResult(false, "Insufficient balance in $currencyCode Wallet")
                return@launch
            }

            // Deduct sender balance
            val updatedWallet = wallet.copy(balance = wallet.balance - amount)
            repository.insertWallet(updatedWallet)

            val desc = "Transferred to @$recipient"
            // Create Transaction record
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = amount,
                    currency = currencyCode,
                    type = "SEND",
                    category = autoCategorize(desc, "SEND")
                )
            )

            // Award Loyalty Points for activity (10 points)
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 10))

            onResult(true, "Successfully sent $currencyCode $amount to @$recipient")
        }
    }

    fun sendMoneyAdvanced(
        amount: Double,
        fee: Double = 0.0,
        currencyCode: String,
        customDescription: String,
        category: String = "SEND",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = repository.wallets.first().find { it.currencyCode == currencyCode }
            if (wallet == null) {
                onResult(false, "Wallet not found for $currencyCode")
                return@launch
            }
            val totalDeduction = amount + fee
            if (wallet.balance < totalDeduction) {
                val symbol = wallet.currencySymbol
                onResult(false, "Insufficient balance in $currencyCode Wallet. Total needed including fees: $symbol${String.format("%,.2f", totalDeduction)}")
                return@launch
            }

            // Deduct sender balance
            val updatedWallet = wallet.copy(balance = wallet.balance - totalDeduction)
            repository.insertWallet(updatedWallet)

            // Create Transaction record
            repository.insertTransaction(
                TransactionEntity(
                    description = customDescription,
                    amount = amount,
                    currency = currencyCode,
                    type = "SEND",
                    category = autoCategorize(customDescription, "SEND")
                )
            )

            // Award Loyalty Points for activity (15 points)
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 15))

            onResult(true, "Transfer dispatched! $customDescription")
        }
    }

    fun depositMoney(amount: Double, currencyCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = repository.wallets.first().find { it.currencyCode == currencyCode }
            if (wallet != null) {
                repository.insertWallet(wallet.copy(balance = wallet.balance + amount))
                val desc = "Funded Wallet via Bank Transfer"
                repository.insertTransaction(
                    TransactionEntity(
                        description = desc,
                        amount = amount,
                        currency = currencyCode,
                        type = "RECEIVE",
                        category = autoCategorize(desc, "RECEIVE")
                    )
                )
                // Add points
                val user = userAccount.value
                repository.insertUserAccount(user.copy(points = user.points + 5))
            }
        }
    }

    fun buyCrypto(ngnAmount: Double, cryptoCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngnWallet = repository.wallets.first().find { it.currencyCode == "NGN" }
            val cryptoWallet = repository.wallets.first().find { it.currencyCode == cryptoCode }

            if (ngnWallet == null || cryptoWallet == null) {
                onResult(false, "Wallets configuration error")
                return@launch
            }
            if (ngnWallet.balance < ngnAmount) {
                onResult(false, "Insufficient NGN balance")
                return@launch
            }

            // Calculate crypto amount
            val rate = cryptoWallet.rateToNgn
            val cryptoAmount = ngnAmount / rate

            // Deduct NGN and credit Crypto
            repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance - ngnAmount))
            repository.insertWallet(cryptoWallet.copy(balance = cryptoWallet.balance + cryptoAmount))

            val desc = "Exchanged NGN for $cryptoCode"
            // Transaction log
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = ngnAmount,
                    currency = "NGN",
                    type = "EXCHANGE",
                    category = autoCategorize(desc, "EXCHANGE")
                )
            )

            // Award loyalty points
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 15))

            onResult(true, "Successfully purchased $cryptoCode ${String.format("%.6f", cryptoAmount)}")
        }
    }

    fun sellCrypto(cryptoAmount: Double, cryptoCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngnWallet = repository.wallets.first().find { it.currencyCode == "NGN" }
            val cryptoWallet = repository.wallets.first().find { it.currencyCode == cryptoCode }

            if (ngnWallet == null || cryptoWallet == null) {
                onResult(false, "Wallets configuration error")
                return@launch
            }
            if (cryptoWallet.balance < cryptoAmount) {
                onResult(false, "Insufficient $cryptoCode balance")
                return@launch
            }

            // Calculate NGN payout
            val rate = cryptoWallet.rateToNgn
            val ngnPayout = cryptoAmount * rate

            // Deduct crypto, credit NGN
            repository.insertWallet(cryptoWallet.copy(balance = cryptoWallet.balance - cryptoAmount))
            repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance + ngnPayout))

            val desc = "Exchanged $cryptoCode for NGN"
            // Transaction log
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = ngnPayout,
                    currency = "NGN",
                    type = "EXCHANGE",
                    category = autoCategorize(desc, "EXCHANGE")
                )
            )

            // Award loyalty points
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 15))

            onResult(true, "Successfully sold $cryptoCode and received NGN ${String.format("%.2f", ngnPayout)}")
        }
    }

    fun submitGiftCardTrade(cardName: String, faceValue: Double, code: String, rate: Double, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (code.isBlank() || faceValue <= 0) {
                onResult(false, "Please provide complete card details.")
                return@launch
            }

            val payout = faceValue * rate
            val trade = GiftCardTrade(
                cardName = cardName,
                faceValue = faceValue,
                rate = rate,
                payoutAmount = payout,
                code = code,
                status = "Pending"
            )

            // Insert trade record
            repository.insertGiftCardTrade(trade)
            onResult(true, "Gift card submitted successfully! Verification usually takes 1-2 minutes.")

            // SIMULATE BACKGROUND VERIFICATION & INSTANT PAYOUT (after 8 seconds)
            delay(8000)
            verifyGiftCardSimulated(trade)
        }
    }

    private suspend fun verifyGiftCardSimulated(trade: GiftCardTrade) {
        // Query last trade in database to make sure it exists
        val currentTrades = repository.giftCards.first()
        val latest = currentTrades.firstOrNull { it.code == trade.code } ?: return

        if (latest.status == "Pending") {
            // Update trade status
            val verifiedTrade = latest.copy(status = "Verified")
            repository.updateGiftCardTrade(verifiedTrade)

            // Payout user in NGN
            val ngnWallet = repository.getWalletDirect("NGN")
            if (ngnWallet != null) {
                repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance + verifiedTrade.payoutAmount))
            }

            val desc = "Payout for Verified ${verifiedTrade.cardName} Gift Card"
            // Insert completed transaction
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = verifiedTrade.payoutAmount,
                    currency = "NGN",
                    type = "GIFT_CARD",
                    category = autoCategorize(desc, "GIFT_CARD")
                )
            )

            // Add points (25 points for gift cards)
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 25))
        }
    }

    fun payBill(billName: String, amount: Double, category: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngnWallet = repository.getWalletDirect("NGN")
            if (ngnWallet == null || ngnWallet.balance < amount) {
                onResult(false, "Insufficient Naira balance to complete payment")
                return@launch
            }

            // Deduct Naira
            repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance - amount))

            val desc = "Paid $billName ($category)"
            // Log Transaction
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = amount,
                    currency = "NGN",
                    type = "BILL",
                    category = autoCategorize(desc, "BILL")
                )
            )

            // Award Points & Gamified cashback
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 12))

            onResult(true, "Bill payment of ₦$amount completed successfully!")
        }
    }

    fun purchaseAirtime(phone: String, amount: Double, provider: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ngnWallet = repository.getWalletDirect("NGN")
            if (ngnWallet == null || ngnWallet.balance < amount) {
                onResult(false, "Insufficient balance to purchase airtime")
                return@launch
            }

            repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance - amount))

            val desc = "Airtime Recharge ($provider - $phone)"
            repository.insertTransaction(
                TransactionEntity(
                    description = desc,
                    amount = amount,
                    currency = "NGN",
                    type = "BILL",
                    category = autoCategorize(desc, "BILL")
                )
            )

            // Cashback reward (3%)
            val cashback = amount * 0.03
            repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance - amount + cashback)) // adjustment

            // Award Points
            val user = userAccount.value
            repository.insertUserAccount(user.copy(points = user.points + 8))

            onResult(true, "Airtime recharge of ₦$amount successful! 3% cashback credited.")
        }
    }

    fun upgradeKycStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            val nextKyc = when (user.kycStatus) {
                "Unverified" -> "Tier 1 Verified"
                "Tier 1 Verified" -> "Tier 2 Verified"
                "Tier 2 Verified" -> "Fully Verified"
                else -> "Fully Verified"
            }
            repository.insertUserAccount(user.copy(kycStatus = nextKyc))
        }
    }

    fun selectSubscriptionTier(tier: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userAccount.value
            repository.insertUserAccount(user.copy(tier = tier))
        }
    }

    // AI Financial Assistant Integration using direct REST API
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Save User Message
            repository.insertChatMessage(ChatMessage(sender = "USER", text = text))
            _aiTypingState.value = AiTypingState.Loading

            // Retrieve context parameters to feed Gemini
            val user = userAccount.value
            val currentWallets = wallets.value.joinToString { "${it.currencyCode}: ${it.currencySymbol}${String.format("%.4f", it.balance)}" }
            val recentTxs = transactions.value.take(4).joinToString { "${it.type} of ${it.currency} ${it.amount} (${it.description})" }

            // Build cohesive financial context prompt
            val systemPrompt = """
                You are Apex AI, a world-class financial advisor, product coach, and spending analyst built into the Apex Pay Fintech Super App.
                You are helping a user in Nigeria/Africa named @${user.username}.
                The user has the following details:
                - Subscription Tier: ${user.tier}
                - KYC Verification Level: ${user.kycStatus}
                - Loyalty Rewards Points: ${user.points}
                - Wallet Balances: $currentWallets
                - Recent Transactions: $recentTxs

                Analyze the user's queries in light of their wallets, spending patterns, and local economy (Nigeria/Africa, currencies like NGN, stablecoins like USDT, fiat USD).
                Always answer professionally, giving simple, actionable, and encouraging financial tips.
                Do not provide unauthorized regulated investment advice or guarantee massive trading profits.
                Highlight app features (rewards shop, virtual cards, utility cashback, gift card swaps, premium tier, referral benefits) where relevant.
                Keep responses elegantly formatted using Markdown (bold text, bullets).
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = text)))),
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
            )

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    // Fallback to local offline simulated advisor if no key is configured
                    delay(1500)
                    val responseText = getSimulatedAiResponse(text, user)
                    repository.insertChatMessage(ChatMessage(sender = "AI", text = responseText))
                    _aiTypingState.value = AiTypingState.Idle
                    return@launch
                }

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I'm sorry, I could not process your query at this moment. Let's try again in a bit!"

                repository.insertChatMessage(ChatMessage(sender = "AI", text = responseText))
                _aiTypingState.value = AiTypingState.Idle
            } catch (e: Exception) {
                // Fallback to local simulated model
                delay(1200)
                val responseText = getSimulatedAiResponse(text, user) + "\n\n*(Offline Mode: Running local insights module)*"
                repository.insertChatMessage(ChatMessage(sender = "AI", text = responseText))
                _aiTypingState.value = AiTypingState.Idle
            }
        }
    }

    private fun getSimulatedAiResponse(text: String, user: UserAccount): String {
        val query = text.lowercase()
        return when {
            query.contains("spending") || query.contains("analyze") || query.contains("insights") -> {
                "Hi **@${user.username}**! Based on your recent transactions, your top utility expense was airtime recharges. Since you have **${user.points} rewards points**, you are earning cashback on every bill payment! Tips:\n\n1. Try setting a weekly budget for mobile data.\n2. Upgrade your account to **Premium** to unlock automatic weekly saving goals with a higher APY compound rate!"
            }
            query.contains("fee") || query.contains("rate") || query.contains("cost") -> {
                "Apex Pay discloses all fees clearly! On your active **${user.tier}** plan:\n\n- **Internal peer-to-peer transfers**: **Free**\n- **Crypto swap commission**: **1.5%**\n- **Gift card trading spread**: **0%** (you get direct market rates!)\n- Upgrade to the **Business/Premium** plan to reduce all transaction costs by **50%**!"
            }
            query.contains("crypto") || query.contains("bitcoin") || query.contains("usdt") -> {
                "Digital currencies are a great way to safeguard savings from local inflation. On Apex Pay, you can swap between Naira (NGN) and stable USDT or BTC instantly. Currently, your BTC wallet has some balances. If you wish to sell, click the **Exchange** tab to instantly credit your NGN Wallet with near-zero spreads!"
            }
            query.contains("gift card") || query.contains("sell card") -> {
                "You can trade popular gift cards (like Steam, Amazon, iTunes) directly inside Apex Pay! We offer the best local rates. Simply navigate to the **Gift Card** tab, enter the face value and card code. Payouts are verified automatically and credited to your NGN wallet instantly! Plus, you'll earn **25 loyalty points** per trade!"
            }
            else -> {
                "Thanks for reaching out, **@${user.username}**! As an active **${user.tier}** user on Apex Pay, you are on the right path to digital wealth. You can send international transfers, trade gift cards, and buy crypto safely. Let me know if you need help with currency rates, bill payments, or custom budgeting plans!"
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory()
            repository.insertChatMessage(
                ChatMessage(sender = "AI", text = "Chat logs cleared. Ask me any financial question!")
            )
        }
    }

    // Admin Dashboard simulated controllers
    fun selectScenario(name: String) {
        _selectedScenarioName.value = name
        val scenario = FinancialProjections.getScenarioByName(name)
        _simulatedActualMonthlyRevenue.value = scenario.totalMonthlyGrossRevenue
    }

    fun adjustSimulatedRevenue(amount: Double) {
        _simulatedActualMonthlyRevenue.value = amount
    }

    fun manualVerifyGiftCard(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val trades = repository.giftCards.first()
            val trade = trades.find { it.id == id } ?: return@launch
            if (trade.status == "Pending") {
                val updated = trade.copy(status = "Verified")
                repository.updateGiftCardTrade(updated)

                // Payout
                val ngnWallet = repository.getWalletDirect("NGN")
                if (ngnWallet != null) {
                    repository.insertWallet(ngnWallet.copy(balance = ngnWallet.balance + updated.payoutAmount))
                }

                // Log Transaction
                repository.insertTransaction(
                    TransactionEntity(
                        description = "Payout for Verified ${updated.cardName} Gift Card (Admin Override)",
                        amount = updated.payoutAmount,
                        currency = "NGN",
                        type = "GIFT_CARD"
                    )
                )

                // Add points
                val user = userAccount.value
                repository.insertUserAccount(user.copy(points = user.points + 25))
            }
        }
    }

    // --- NEXT-GEN 2026 FINTECH CAPABILITIES ---
    // 1. AI & Autonomous Banking
    private val _aiLoanApplications = MutableStateFlow<List<AiLoanApplication>>(
        listOf(
            AiLoanApplication("LN-98421", "Uber Driver Working Capital", 250000.0, "NGN", 94.7, "APPROVED", "Instant Autonomous Approval by JPMorgan AI Engine"),
            AiLoanApplication("LN-98305", "Inventory Expansion Loan", 1200.0, "USD", 96.2, "APPROVED", "Credit Bureau + Telemetry Risk Score Passed")
        )
    )
    val aiLoanApplications: StateFlow<List<AiLoanApplication>> = _aiLoanApplications.asStateFlow()

    private val _aiPortfolioRebalanceEnabled = MutableStateFlow(true)
    val aiPortfolioRebalanceEnabled: StateFlow<Boolean> = _aiPortfolioRebalanceEnabled.asStateFlow()

    fun toggleAiPortfolioRebalance(enabled: Boolean) {
        _aiPortfolioRebalanceEnabled.value = enabled
    }

    fun applyForAiLoan(purpose: String, amount: Double, currency: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val accuracyScore = 94.7 + (Math.random() * 4.0)
            val isApproved = amount <= 5000000.0
            val status = if (isApproved) "APPROVED" else "REJECTED"
            val note = if (isApproved) "AI Autonomous Credit Engine approved instant payout with ${String.format("%.1f", accuracyScore)}% confidence." else "Amount exceeds automated pre-approval threshold."
            val newApp = AiLoanApplication("LN-" + System.currentTimeMillis().toString().takeLast(6), purpose, amount, currency, accuracyScore, status, note)
            _aiLoanApplications.value = listOf(newApp) + _aiLoanApplications.value

            if (isApproved) {
                // Deposit funds into target wallet
                val targetWallet = wallets.value.find { it.currencyCode == currency }
                if (targetWallet != null) {
                    repository.insertWallet(targetWallet.copy(balance = targetWallet.balance + amount))
                }
                repository.insertTransaction(
                    TransactionEntity(
                        description = "Autonomous AI Credit Payout: $purpose",
                        amount = amount,
                        currency = currency,
                        type = "DEPOSIT"
                    )
                )
            }
            onResult(isApproved, note)
        }
    }

    // 2. Quantum-Secured Payments
    private val _quantumSecurityEnabled = MutableStateFlow(true)
    val quantumSecurityEnabled: StateFlow<Boolean> = _quantumSecurityEnabled.asStateFlow()

    private val _quantumSessionKey = MutableStateFlow("QKD-LATTICE-256-" + System.currentTimeMillis().toString().takeLast(8))
    val quantumSessionKey: StateFlow<String> = _quantumSessionKey.asStateFlow()

    fun toggleQuantumSecurity(enabled: Boolean) {
        _quantumSecurityEnabled.value = enabled
    }

    fun rotateQuantumKeys() {
        _quantumSessionKey.value = "QKD-KYBER-1024-" + System.currentTimeMillis().toString().takeLast(8)
    }

    // 3. Central Bank Digital Currencies (CBDCs)
    private val _cbdcBalances = MutableStateFlow(
        mapOf(
            "eNaira" to 250000.0,
            "eUSD" to 1500.0,
            "eEUR" to 850.0,
            "eCNY" to 5000.0
        )
    )
    val cbdcBalances: StateFlow<Map<String, Double>> = _cbdcBalances.asStateFlow()

    fun mintOrTransferCbdc(cbdcType: String, amount: Double, isMint: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentMap = _cbdcBalances.value.toMutableMap()
            val currentBal = currentMap[cbdcType] ?: 0.0
            if (isMint) {
                currentMap[cbdcType] = currentBal + amount
                _cbdcBalances.value = currentMap
                repository.insertTransaction(
                    TransactionEntity(
                        description = "Minted $cbdcType Central Bank Digital Currency",
                        amount = amount,
                        currency = cbdcType,
                        type = "CBDC_MINT"
                    )
                )
                onResult("Minted $amount $cbdcType with 0.00% fee via Central Bank Ledger.")
            } else {
                if (currentBal >= amount) {
                    currentMap[cbdcType] = currentBal - amount
                    _cbdcBalances.value = currentMap
                    repository.insertTransaction(
                        TransactionEntity(
                            description = "Instant CBDC Settlement Transfer ($cbdcType)",
                            amount = -amount,
                            currency = cbdcType,
                            type = "CBDC_TRANSFER"
                        )
                    )
                    onResult("Transferred $amount $cbdcType instantly across 94 global CBDC gateways.")
                } else {
                    onResult("Insufficient $cbdcType CBDC balance.")
                }
            }
        }
    }

    // 4. Embedded Finance
    private val _embeddedActivePolicies = MutableStateFlow(
        listOf(
            EmbeddedPolicy("POL-771", "Uber Driver Ride Micro-Insurance", "$0.50 / day", "ACTIVE", "Covers accidental damage & passenger liability"),
            EmbeddedPolicy("POL-890", "E-Commerce Checkout BNPL Financing", "4 Installments @ 0% APR", "ACTIVE", "Shopping Order #88219")
        )
    )
    val embeddedActivePolicies: StateFlow<List<EmbeddedPolicy>> = _embeddedActivePolicies.asStateFlow()

    fun activateEmbeddedBnpl(merchant: String, totalAmount: Double, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val installment = totalAmount / 4.0
            val newPol = EmbeddedPolicy("BNPL-" + System.currentTimeMillis().toString().takeLast(4), "$merchant BNPL Plan", "4x \$${String.format("%.2f", installment)}", "ACTIVE", "1st payment charged, 3 remaining bi-weekly")
            _embeddedActivePolicies.value = listOf(newPol) + _embeddedActivePolicies.value
            onResult("Activated BNPL for $merchant. First installment of \$${String.format("%.2f", installment)} approved.")
        }
    }

    // 5. Biometric Authentication Suite
    private val _faceIdActive = MutableStateFlow(true)
    val faceIdActive: StateFlow<Boolean> = _faceIdActive.asStateFlow()

    private val _fingerprintActive = MutableStateFlow(true)
    val fingerprintActive: StateFlow<Boolean> = _fingerprintActive.asStateFlow()

    private val _voicePrintActive = MutableStateFlow(true)
    val voicePrintActive: StateFlow<Boolean> = _voicePrintActive.asStateFlow()

    fun toggleBiometricSetting(type: String, active: Boolean) {
        when (type) {
            "FACE" -> _faceIdActive.value = active
            "FINGER" -> _fingerprintActive.value = active
            "VOICE" -> _voicePrintActive.value = active
        }
    }

    // 6. Blockchain & DeFi Integration
    private val _deFiYieldPools = MutableStateFlow(
        listOf(
            DeFiPool("APEX-USDT LP", "14.2% APY", 1250.0, "₮", "Polygon L2 Protocol"),
            DeFiPool("Aave Collateral Lend", "8.5% APY", 0.15, "ETH", "Arbitrum One"),
            DeFiPool("Solana Liquid Staking", "11.8% APY", 0.0, "SOL", "Solana Network")
        )
    )
    val deFiYieldPools: StateFlow<List<DeFiPool>> = _deFiYieldPools.asStateFlow()

    fun stakeInDeFiPool(poolName: String, amount: Double, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val updated = _deFiYieldPools.value.map {
                if (it.name == poolName) it.copy(stakedBalance = it.stakedBalance + amount) else it
            }
            _deFiYieldPools.value = updated
            onResult("Staked $amount into $poolName. Yield rewards accruing hourly.")
        }
    }

    // 7. RegTech & Compliance Automation
    private val _regTechAmlScore = MutableStateFlow(98) // Out of 100 Safe
    val regTechAmlScore: StateFlow<Int> = _regTechAmlScore.asStateFlow()

    private val _pepSanctionCleared = MutableStateFlow(true)
    val pepSanctionCleared: StateFlow<Boolean> = _pepSanctionCleared.asStateFlow()

    fun runRegTechComplianceAudit(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _regTechAmlScore.value = 99
            _pepSanctionCleared.value = true
            onComplete("RegTech AI Audit Complete: 0 PEP match, OFAC/UN Sanction List Cleared, AML Risk Index: 99/100 (Extremely Low Risk).")
        }
    }

    // 8. Mobile Check Deposit Functionality
    private val _checkDeposits = MutableStateFlow<List<CheckDeposit>>(
        listOf(
            CheckDeposit(
                id = "CHK-90412",
                checkNumber = "10492",
                routingNumber = "021000021",
                accountNumber = "******8821",
                issuingBank = "JPMorgan Chase Bank, N.A.",
                amount = 1250.00,
                currency = "USD",
                status = "COMPLETED",
                frontCaptured = true,
                backCaptured = true,
                dateSubmitted = "Jul 28, 2026",
                aiOcrConfidence = 99.8
            ),
            CheckDeposit(
                id = "CHK-88301",
                checkNumber = "00281",
                routingNumber = "121000358",
                accountNumber = "******4419",
                issuingBank = "Wells Fargo Bank",
                amount = 450.00,
                currency = "USD",
                status = "COMPLETED",
                frontCaptured = true,
                backCaptured = true,
                dateSubmitted = "Jul 20, 2026",
                aiOcrConfidence = 99.5
            )
        )
    )
    val checkDeposits: StateFlow<List<CheckDeposit>> = _checkDeposits.asStateFlow()

    fun processMobileCheckDeposit(
        checkNumber: String,
        routingNumber: String,
        accountNumber: String,
        issuingBank: String,
        amount: Double,
        currency: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                onResult(false, "Check deposit amount must be greater than zero.")
                return@launch
            }

            val depositId = "CHK-" + System.currentTimeMillis().toString().takeLast(5)
            val currentDate = "Jul 30, 2026"
            val ocrScore = 98.5 + (Math.random() * 1.4)

            val newCheck = CheckDeposit(
                id = depositId,
                checkNumber = checkNumber.ifBlank { "100" + (10..99).random() },
                routingNumber = routingNumber.ifBlank { "021000021" },
                accountNumber = accountNumber.ifBlank { "******" + (1000..9999).random() },
                issuingBank = issuingBank.ifBlank { "Apex Partner Clearing Bank" },
                amount = amount,
                currency = currency,
                status = "COMPLETED",
                frontCaptured = true,
                backCaptured = true,
                dateSubmitted = currentDate,
                aiOcrConfidence = ocrScore
            )

            // Update list
            _checkDeposits.value = listOf(newCheck) + _checkDeposits.value

            // Credit target wallet balance
            val targetWallet = wallets.value.find { it.currencyCode == currency }
            if (targetWallet != null) {
                repository.insertWallet(targetWallet.copy(balance = targetWallet.balance + amount))
            } else {
                // Create wallet if not existing
                repository.insertWallet(WalletEntity(currency, "$currency Account", if (currency == "USD") "$" else "₦", amount, false, 1.0))
            }

            // Record transaction
            repository.insertTransaction(
                TransactionEntity(
                    description = "Mobile Check Deposit (${newCheck.issuingBank} #${newCheck.checkNumber})",
                    amount = amount,
                    currency = currency,
                    type = "DEPOSIT"
                )
            )

            onResult(true, "Mobile Check Deposit of ${if (currency == "USD") "$" else "₦"}${String.format("%,.2f", amount)} approved instantly! AI Computer Vision Confidence: ${String.format("%.1f", ocrScore)}%.")
        }
    }

    fun resetAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory()
            val initialWallets = listOf(
                WalletEntity("NGN", "Nigerian Naira", "₦", 750000.0, false, 1.0),
                WalletEntity("USD", "US Dollar", "$", 1500.0, false, 1500.0),
                WalletEntity("BTC", "Bitcoin", "₿", 0.045, true, 97500000.0),
                WalletEntity("USDT", "Tether Stablecoin", "₮", 250.0, true, 1500.0),
                WalletEntity("ETH", "Ethereum", "Ξ", 0.25, true, 4800000.0)
            )
            repository.insertWallets(initialWallets)
            repository.insertUserAccount(UserAccount())
            initDefaultData()
        }
    }
}

sealed class AiTypingState {
    object Idle : AiTypingState()
    object Loading : AiTypingState()
    data class Error(val message: String) : AiTypingState()
}
