package com.example.retrodatabases.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT d.id AS id,
               d.name AS name,
               d.description AS description,
               d.colorHex AS colorHex,
               (SELECT COUNT(*) FROM records r WHERE r.databaseId = d.id) AS recordCount
        FROM databases d
        ORDER BY d.id
        """
    )
    fun observeDatabases(): Flow<List<DatabaseSummary>>

    @Query("SELECT * FROM databases WHERE id = :databaseId")
    fun observeDatabase(databaseId: Long): Flow<DatabaseEntity?>

    @Query("SELECT * FROM records WHERE databaseId = :databaseId ORDER BY year, title")
    fun observeRecords(databaseId: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun observeRecord(recordId: Long): Flow<RecordEntity?>

    @Query("UPDATE records SET favorite = NOT favorite WHERE id = :recordId")
    suspend fun toggleFavorite(recordId: Long)
}
