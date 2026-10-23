package com.example.poswarnus.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A completed cash sale. Whole Rupiah everywhere; [id] is the idempotency key for the later sync. */
@Entity(tableName = "transactions")
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
)

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
}
