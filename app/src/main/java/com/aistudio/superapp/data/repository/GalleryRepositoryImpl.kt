package com.aistudio.superapp.data.repository

import com.aistudio.superapp.data.local.GeneratedImageEntity
import com.aistudio.superapp.data.local.GalleryDao
import com.aistudio.superapp.domain.model.GeneratedImage
import com.aistudio.superapp.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class GalleryRepositoryImpl(private val dao: GalleryDao) : GalleryRepository {
    override val images: Flow<List<GeneratedImage>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(source: String, prompt: String, modelId: String): GeneratedImage {
        val entity = GeneratedImageEntity(UUID.randomUUID().toString(), source, prompt, modelId, System.currentTimeMillis())
        dao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun delete(id: String) = dao.delete(id)

    private fun GeneratedImageEntity.toDomain() = GeneratedImage(id, source, prompt, modelId, createdAt)
}
