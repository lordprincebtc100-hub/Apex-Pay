package com.example.data

import kotlinx.coroutines.flow.Flow

class ApexRepository(private val dao: ApexDao) {
    val userAccount: Flow<UserAccount?> = dao.getUserAccountFlow()
    val wallets: Flow<List<WalletEntity>> = dao.getAllWalletsFlow()
    val transactions: Flow<List<TransactionEntity>> = dao.getAllTransactionsFlow()
    val giftCards: Flow<List<GiftCardTrade>> = dao.getAllGiftCardTradesFlow()
    val chatMessages: Flow<List<ChatMessage>> = dao.getAllChatMessagesFlow()

    suspend fun getUserAccountDirect(): UserAccount? = dao.getUserAccountDirect()

    suspend fun insertUserAccount(user: UserAccount) {
        dao.insertUserAccount(user)
    }

    suspend fun getWalletDirect(code: String): WalletEntity? = dao.getWalletDirect(code)

    suspend fun insertWallet(wallet: WalletEntity) {
        dao.insertWallet(wallet)
    }

    suspend fun insertWallets(wallets: List<WalletEntity>) {
        dao.insertWallets(wallets)
    }

    suspend fun insertTransaction(transaction: TransactionEntity) {
        dao.insertTransaction(transaction)
    }

    suspend fun insertGiftCardTrade(trade: GiftCardTrade) {
        dao.insertGiftCardTrade(trade)
    }

    suspend fun updateGiftCardTrade(trade: GiftCardTrade) {
        dao.updateGiftCardTrade(trade)
    }

    suspend fun insertChatMessage(message: ChatMessage) {
        dao.insertChatMessage(message)
    }

    suspend fun clearChatHistory() {
        dao.clearChatHistory()
    }
}
