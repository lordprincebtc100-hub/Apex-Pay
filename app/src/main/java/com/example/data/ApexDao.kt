package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApexDao {
    // User Account
    @Query("SELECT * FROM user_account WHERE id = :userId LIMIT 1")
    fun getUserAccountFlow(userId: String = "default_user"): Flow<UserAccount?>

    @Query("SELECT * FROM user_account WHERE id = :userId LIMIT 1")
    suspend fun getUserAccountDirect(userId: String = "default_user"): UserAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserAccount(user: UserAccount)

    // Wallets
    @Query("SELECT * FROM wallets")
    fun getAllWalletsFlow(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE currencyCode = :code LIMIT 1")
    suspend fun getWalletDirect(code: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: WalletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallets(wallets: List<WalletEntity>)

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    // Gift Cards
    @Query("SELECT * FROM gift_card_trades ORDER BY timestamp DESC")
    fun getAllGiftCardTradesFlow(): Flow<List<GiftCardTrade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGiftCardTrade(trade: GiftCardTrade)

    @Update
    suspend fun updateGiftCardTrade(trade: GiftCardTrade)

    // Chat Messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessagesFlow(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()
}
