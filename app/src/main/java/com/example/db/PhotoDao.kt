package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_metadata ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoMetadata): Long

    @Query("DELETE FROM photo_metadata WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("SELECT * FROM photo_metadata WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoMetadata?
}
