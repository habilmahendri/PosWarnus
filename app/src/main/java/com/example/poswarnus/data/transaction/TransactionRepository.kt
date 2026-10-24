package com.example.poswarnus.data.transaction

import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.sync.SyncScheduler
import com.example.poswarnus.data.sync.SyncSummary
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.OrderTotals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Everything a sale needs, frozen when the payment dialog opened. [id] is reused on retry. */
data class SaleSnapshot(
    val id: String,
    val createdAt: Long,
    val cashierId: String,
    val lines: List<CartLine>,
    val totals: OrderTotals,
)

interface TransactionRepository {
    /** Writes the sale locally. Saving an id that is already stored succeeds without writing again. */
    suspend fun save(sale: SaleSnapshot, cashReceived: Long): Result<Unit>

    /** Live counts behind the sync pill (training sales excluded). */
    val syncSummary: Flow<SyncSummary>

    /** The signed-in cashier's real sales created at or after [fromMillis], newest first. Empty when nobody is signed in. */
    fun observeHistory(fromMillis: Long): Flow<List<HistoryEntry>>
}

class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    private val syncScheduler: SyncScheduler,
    private val currentCashierId: () -> String?,
) : TransactionRepository {
    // The getter reads the cashier id each time it is accessed. DashboardViewModel is created per dashboard
    // session after login, so the id is already the signed-in cashier's when it collects this flow.
    override val syncSummary: Flow<SyncSummary>
        get() = dao.observeSyncSummary(currentCashierId() ?: "").map { SyncSummary(it.pendingCount, it.offlineCount, it.lastSyncedAt) }

    override fun observeHistory(fromMillis: Long): Flow<List<HistoryEntry>> {
        val cashierId = currentCashierId() ?: return flowOf(emptyList())
        return dao.observeHistory(cashierId, fromMillis).map { rows -> rows.map { it.toHistoryEntry() } }
    }

    override suspend fun save(sale: SaleSnapshot, cashReceived: Long): Result<Unit> =
        try {
            val header = TransactionEntity(
                id = sale.id,
                createdAt = sale.createdAt,
                cashierId = sale.cashierId,
                subtotal = sale.totals.subtotal,
                taxLabel = sale.totals.taxLabel,
                taxBasisPoints = sale.totals.taxBasisPoints,
                taxAmount = sale.totals.taxAmount,
                grandTotal = sale.totals.grandTotal,
                cashReceived = cashReceived,
                changeAmount = cashReceived - sale.totals.grandTotal,
                isTraining = false,
            )
            val lines = sale.lines.mapIndexed { index, line ->
                TransactionLineEntity(
                    id = "${sale.id}:$index",
                    transactionId = sale.id,
                    menuItemId = line.menuItemId,
                    name = line.name,
                    basePrice = line.basePrice,
                    quantity = line.quantity,
                    selectionsJson = SelectionsJson.encode(line.selections),
                )
            }
            dao.insertSale(header, lines)
            // The sale is already stored; a scheduler failure must not turn the save into a failure.
            try {
                syncScheduler.requestSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ignored on purpose; the periodic work and the next refresh pick the sale up
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
