package com.alsafa.native8.data.operational

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Exact column/primary-key mapping for OperationalDatabase v5.
 * Prepared for Room ownership; do not register these entities in AppDatabase
 * until the existing UNIQUE constraints and schema identity have been migrated
 * and verified on a copy of a real database.
 */
@Entity(tableName = "source_imports")
data class SourceImportEntity(
    @androidx.room.PrimaryKey val source_sha256: String,
    val source_run_id: Long,
    val imported_at: Long,
    val product_count: Int
)

@Entity(
    tableName = "products",
    primaryKeys = ["source_sha256", "source_ordinal"],
    foreignKeys = [ForeignKey(
        entity = SourceImportEntity::class,
        parentColumns = ["source_sha256"],
        childColumns = ["source_sha256"],
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [
        Index(value = ["source_sha256", "legacy_id"], name = "idx_products_legacy_id"),
        Index(value = ["source_sha256", "variant_group_id"], name = "idx_products_group")
    ]
)
data class ProductEntity(
    val source_sha256: String,
    val source_ordinal: Int,
    val legacy_id: String,
    val name: String?,
    val stock_text: String?,
    val sale_price_text: String?,
    val cost_text: String?,
    val currency: String?,
    val barcode: String?,
    val variant_group_id: String?,
    val variant_color: String?,
    val original_json: String
)

@Entity(
    tableName = "product_edits",
    primaryKeys = ["source_sha256", "legacy_id"],
    foreignKeys = [ForeignKey(
        entity = SourceImportEntity::class,
        parentColumns = ["source_sha256"],
        childColumns = ["source_sha256"],
        onDelete = ForeignKey.NO_ACTION
    )]
)
data class ProductEditEntity(
    val source_sha256: String,
    val legacy_id: String,
    val edited_json: String,
    val base_sha256: String,
    val updated_at: Long
)

@Entity(tableName = "local_products")
data class LocalProductEntity(
    @androidx.room.PrimaryKey val local_id: String,
    val product_json: String,
    val created_at: Long,
    val updated_at: Long
)

/**
 * Original SQLite v5 declares UNIQUE(parent_source,parent_id,color_name)
 * as a table constraint. Room models uniqueness as an index instead.
 * This entity MUST NOT be activated without an explicit, tested migration
 * of that constraint and its index metadata.
 */
@Entity(
    tableName = "product_variants_local",
    indices = [Index(
        value = ["parent_source", "parent_id", "color_name"],
        unique = true,
        name = "idx_variants_parent_color_unique"
    )]
)
data class ProductVariantEntity(
    @androidx.room.PrimaryKey val variant_id: String,
    val parent_source: String,
    val parent_id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val color_name: String,
    val variant_json: String,
    val created_at: Long,
    val updated_at: Long
)

@Entity(
    tableName = "product_stock_movements",
    indices = [Index(
        value = ["parent_source", "parent_id", "created_at"],
        name = "idx_product_stock_movements_parent"
    )]
)
data class ProductStockMovementEntity(
    @androidx.room.PrimaryKey val movement_id: String,
    val parent_source: String,
    val parent_id: String,
    val variant_id: String?,
    val quantity_delta: String,
    val quantity_before: String,
    val quantity_after: String,
    val note: String,
    val created_at: Long
)

/**
 * Read-only queries until the complete v5-to-Room migration is verified.
 * No REPLACE conflict policy: financial and inventory history must be immutable.
 */
@Dao
interface OperationalInventoryReadDao {
    @Query("SELECT * FROM source_imports WHERE source_sha256 = :source LIMIT 1")
    suspend fun source(source: String): SourceImportEntity?

    @Query("SELECT * FROM products WHERE source_sha256 = :source ORDER BY source_ordinal")
    suspend fun importedProducts(source: String): List<ProductEntity>

    @Query("SELECT * FROM product_edits WHERE source_sha256 = :source AND legacy_id = :legacyId LIMIT 1")
    suspend fun productEdit(source: String, legacyId: String): ProductEditEntity?

    @Query("SELECT * FROM local_products WHERE local_id = :id LIMIT 1")
    suspend fun localProduct(id: String): LocalProductEntity?

    @Query("SELECT * FROM product_variants_local WHERE parent_source = :source AND parent_id = :parentId ORDER BY color_name")
    suspend fun variants(source: String, parentId: String): List<ProductVariantEntity>

    @Query("SELECT * FROM product_stock_movements WHERE parent_source = :source AND parent_id = :parentId ORDER BY created_at, movement_id")
    suspend fun stockHistory(source: String, parentId: String): List<ProductStockMovementEntity>
}
