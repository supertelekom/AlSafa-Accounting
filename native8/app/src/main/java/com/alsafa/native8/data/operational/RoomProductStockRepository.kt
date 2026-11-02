package com.alsafa.native8.data.operational

import com.alsafa.native8.sales.AppDatabase
import java.math.BigDecimal

/** Room-backed stock movement ledger; every adjustment is transactional. */
class RoomProductStockRepository(private val db: AppDatabase) {
    private val inventory = RoomInventoryRepository(db)
    private val dao get() = db.operationalInventoryWriteDao()

    data class Movement(val delta: String, val before: String, val after: String, val note: String, val time: Long)

    suspend fun history(source: String, parentId: String, variantId: String? = null): List<Movement> =
        dao.stockHistory(source, parentId, variantId).map {
            Movement(it.quantity_delta, it.quantity_before, it.quantity_after, it.note, it.created_at)
        }

    suspend fun currentStock(source: String, parentId: String, variantId: String? = null): String =
        inventory.stock(source, parentId, variantId).stripTrailingZeros().toPlainString()

    suspend fun adjust(source: String, parentId: String, variantId: String?, deltaText: String, note: String): String {
        val normalized = buildString {
            for (char in deltaText.trim()) {
                val mapped: Char? = when (char) {
                    in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                    in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                    '٫', ',' -> '.'
                    '−', '–' -> '-'
                    '＋' -> '+'
                    '٬', ' ', '\u200e', '\u200f' -> null
                    else -> char
                }
                if (mapped != null) append(mapped)
            }
        }
        val delta = normalized.toBigDecimalOrNull() ?: error("فرق الكمية غير صالح")
        return inventory.adjust(source, parentId, variantId, delta, note)
    }
}
