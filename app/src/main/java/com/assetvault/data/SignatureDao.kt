package com.assetvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * SignatureDao - Data Access Object for SignatureEntity.
 */
@Dao
interface SignatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SignatureEntity): Long

    @Update
    suspend fun update(entity: SignatureEntity)

    @Query("SELECT * FROM signatures ORDER BY timestamp DESC")
    suspend fun getAll(): List<SignatureEntity>

    @Query("SELECT * FROM signatures WHERE ownerEmail = :email ORDER BY timestamp DESC")
    suspend fun getByOwnerEmail(email: String): List<SignatureEntity>

    @Query("SELECT * FROM signatures WHERE id = :id")
    suspend fun getById(id: Long): SignatureEntity?

    @Query("SELECT * FROM signatures WHERE uri = :uri AND ownerEmail = :email LIMIT 1")
    suspend fun getByUriAndOwner(uri: String, email: String): SignatureEntity?

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM signatures")
    suspend fun getCount(): Int

    @Query("SELECT * FROM signatures WHERE status = :status")
    suspend fun getByStatus(status: String): List<SignatureEntity>
}