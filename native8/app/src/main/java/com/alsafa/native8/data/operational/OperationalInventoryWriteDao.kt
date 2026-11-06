package com.alsafa.native8.data.operational

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room-native inventory access. All multi-table writes must be wrapped by
 * AppDatabase.withTransaction at the caller; never replace historical rows.
 */
@Dao
interface OperationalInventoryWriteDao {
    @Query("SELECT * FROM local_products ORDER BY created_at DESC,local_id LIMIT 1 OFFSET :offset")
    suspend fun searchLocalPage(offset:Int):List<LocalProductEntity>
    @Query("SELECT * FROM product_variants_local ORDER BY variant_id LIMIT 1 OFFSET :offset")
    suspend fun searchVariantPage(offset:Int):List<ProductVariantEntity>
    @Query("SELECT (SELECT COUNT(*) FROM products)||':'||(SELECT COUNT(*) FROM product_edits)||':'||(SELECT COALESCE(SUM(updated_at),0) FROM product_edits)||':'||(SELECT COUNT(*) FROM local_products)||':'||(SELECT COALESCE(SUM(updated_at),0) FROM local_products)||':'||(SELECT COUNT(*) FROM product_variants_local)||':'||(SELECT COALESCE(SUM(updated_at),0) FROM product_variants_local)")
    suspend fun searchRevision():String

    @Query("SELECT * FROM source_imports WHERE source_sha256=:source LIMIT 1")
    suspend fun sourceImport(source: String): SourceImportEntity?

    @Query("SELECT source_sha256 FROM source_imports ORDER BY source_run_id DESC LIMIT 1")
    suspend fun latestSource(): String?

    @Query("SELECT source_sha256 FROM source_imports WHERE source_run_id=:runId LIMIT 1")
    suspend fun sourceForRun(runId: Long): String?

    @Query("SELECT * FROM products WHERE source_sha256=:source AND legacy_id=:id LIMIT 2")
    suspend fun byOriginalId(source: String, id: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE source_sha256=:source AND variant_group_id=:groupId ORDER BY source_ordinal")
    suspend fun importedVariants(source: String, groupId: String): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE source_sha256=:source")
    suspend fun productCount(source: String): Int

    @Query("SELECT * FROM products WHERE source_sha256=:source ORDER BY source_ordinal LIMIT :limit OFFSET :offset")
    suspend fun productPage(source: String, limit: Int, offset: Int): List<ProductEntity>

    @Query("SELECT * FROM product_edits WHERE source_sha256=:source AND legacy_id=:id LIMIT 1")
    suspend fun edit(source: String, id: String): ProductEditEntity?

    @Query("SELECT * FROM local_products ORDER BY created_at DESC")
    suspend fun localProducts(): List<LocalProductEntity>

    @Query("SELECT * FROM local_products WHERE local_id=:id LIMIT 1")
    suspend fun localProduct(id: String): LocalProductEntity?

    @Query("SELECT * FROM products")
    suspend fun allImported(): List<ProductEntity>

    @Query("SELECT * FROM product_edits")
    suspend fun allEdits(): List<ProductEditEntity>

    @Query("SELECT * FROM product_variants_local")
    suspend fun allVariants(): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants_local WHERE variant_id=:id LIMIT 1")
    suspend fun variant(id: String): ProductVariantEntity?

    @Query("SELECT * FROM product_variants_local WHERE parent_source=:source AND parent_id=:parentId ORDER BY created_at")
    suspend fun variants(source: String, parentId: String): List<ProductVariantEntity>

    @Query("SELECT * FROM product_stock_movements WHERE parent_source=:source AND parent_id=:parentId AND ((:variantId IS NULL AND variant_id IS NULL) OR variant_id=:variantId) ORDER BY created_at DESC")
    suspend fun stockHistory(source: String, parentId: String, variantId: String?): List<ProductStockMovementEntity>

    @Query("SELECT COUNT(*) FROM product_stock_movements WHERE variant_id=:id")
    suspend fun movementCount(id: String): Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertSource(value: SourceImportEntity)

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertProducts(values: List<ProductEntity>)

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertLocal(value: LocalProductEntity)

    @Update
    suspend fun updateLocal(value: LocalProductEntity): Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertEdit(value: ProductEditEntity)

    @Update
    suspend fun updateEdit(value: ProductEditEntity): Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertVariant(value: ProductVariantEntity)

    @Update
    suspend fun updateVariant(value: ProductVariantEntity): Int

    @Query("DELETE FROM product_variants_local WHERE variant_id=:id AND parent_source=:source AND parent_id=:parentId")
    suspend fun deleteVariant(id: String, source: String, parentId: String): Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(value: ProductStockMovementEntity)
}
