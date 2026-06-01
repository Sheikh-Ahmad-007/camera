package com.example.db

import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotos: Flow<List<PhotoMetadata>> = photoDao.getAllPhotos()

    suspend fun insertPhoto(photo: PhotoMetadata): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(id: Long) {
        photoDao.deletePhotoById(id)
    }

    suspend fun getPhoto(id: Long): PhotoMetadata? {
        return photoDao.getPhotoById(id)
    }
}
