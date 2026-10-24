package com.example.poswarnus.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.poswarnus.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

/** A completed cash sale. Whole Rupiah everywhere; [id] is the idempotency key for the later sync. */
@Entity(tableName = "transactions", indices = [Index("syncStatus")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val cashierId: String,
    val subtotal: Long,
    val taxLabel: String,
    val taxBasisPoints: Int,
    val taxAmount: Long,
    val grandTotal: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    @ColumnInfo(defaultValue = "0") val isTraining: Boolean = false,
    @ColumnInfo(defaultValue = "'PENDING'") val syncStatus: String = SyncStatus.PENDING,
    val syncedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
)

/** Aggregate behind the sync pill. Training sales are excluded by the query. */
data class SyncSummaryRow(val pendingCount: Int, val offlineCount: Int, val lastSyncedAt: Long?)

@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class TransactionLineEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val menuItemId: String,
    val name: String,
    val basePrice: Long,
    val quantity: Int,
    val selectionsJson: String,
)

/** A sale header with its lines, for the history list. Line order is not guaranteed; sort by the id suffix. */
data class TransactionWithLines(
    @Embedded val header: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId") val lines: List<TransactionLineEntity>,
)

@Dao
abstract class TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertHeader(header: TransactionEntity): Long

    @Insert
    abstract suspend fun insertLines(lines: List<TransactionLineEntity>)

    /** True when stored; false when [header]'s id already exists (nothing is written then). */
    @Transaction
    open suspend fun insertSale(header: TransactionEntity, lines: List<TransactionLineEntity>): Boolean {
        if (insertHeader(header) == -1L) return false
        insertLines(lines)
        return true
    }

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transaction_lines WHERE transactionId = :transactionId ORDER BY rowid")
    abstract suspend fun linesFor(transactionId: String): List<TransactionLineEntity>

    /** Next PENDING real sales of [cashierId], oldest first, strictly after the keyset cursor. */
    @Query(
        """SELECT * FROM transactions
          WHERE syncStatus = 'PENDING' AND isTraining = 0 AND cashierId = :cashierId
            AND (createdAt > :afterCreatedAt OR (createdAt = :afterCreatedAt AND id > :afterId))
          ORDER BY createdAt, id LIMIT :limit""",
    )
    abstract suspend fun pendingBatch(cashierId: String, afterCreatedAt: Long, afterId: String, limit: Int): List<TransactionEntity>

    @Query("UPDATE transactions SET syncStatus = 'SYNCED', syncedAt = :at, lastSyncError = NULL WHERE id = :id")
    abstract suspend fun markSynced(id: String, at: Long)

    @Query("UPDATE transactions SET syncAttempts = syncAttempts + 1, lastSyncError = :error WHERE id = :id")
    abstract suspend fun recordFailure(id: String, error: String)

    @Query(
        """SELECT
            COALESCE(SUM(CASE WHEN syncStatus = 'PENDING' THEN 1 ELSE 0 END), 0) AS pendingCount,
            COALESCE(SUM(CASE WHEN syncStatus = 'PENDING' AND lastSyncError LIKE 'offline:%' THEN 1 ELSE 0 END), 0) AS offlineCount,
            MAX(syncedAt) AS lastSyncedAt
          FROM transactions WHERE isTraining = 0 AND cashierId = :cashierId""",
    )
    abstract fun observeSyncSummary(cashierId: String): Flow<SyncSummaryRow>

    /** Real sales of [cashierId] created at or after [fromMillis], newest first, with their lines. */
    @Transaction
    @Query(
        "SELECT * FROM transactions WHERE cashierId = :cashierId AND isTraining = 0 AND createdAt >= :fromMillis ORDER BY createdAt DESC, id",
    )
    abstract fun observeHistory(cashierId: String, fromMillis: Long): Flow<List<TransactionWithLines>>
}
