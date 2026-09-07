package com.aistudio.superapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id=:id LIMIT 1")
    suspend fun get(id: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET title=:title, updatedAt=:updatedAt WHERE id=:id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE sessions SET updatedAt=:updatedAt WHERE id=:id")
    suspend fun touch(id: String, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId=:sessionId ORDER BY createdAt ASC, id ASC")
    fun observeForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId=:sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun listForSession(sessionId: String): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET content=:content WHERE id=:id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE sessionId=:sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("""
        SELECT m.id AS messageId, m.sessionId AS sessionId, s.title AS sessionTitle,
               m.role AS role, m.content AS content, m.createdAt AS createdAt
        FROM messages m
        JOIN message_fts ON message_fts.rowid = m.id
        LEFT JOIN sessions s ON s.id = m.sessionId
        WHERE message_fts MATCH :ftsQuery
        ORDER BY m.createdAt DESC
        LIMIT :limit
    """)
    suspend fun search(ftsQuery: String, limit: Int = 100): List<SearchHitDb>
}

@Dao
interface GalleryDao {
    @Query("SELECT * FROM generated_images ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GeneratedImageEntity>>

    @Upsert
    suspend fun upsert(image: GeneratedImageEntity)

    @Query("DELETE FROM generated_images WHERE id=:id")
    suspend fun delete(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE enabled=1 ORDER BY builtIn DESC, displayName ASC")
    fun observeEnabled(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM models ORDER BY builtIn DESC, displayName ASC")
    fun observeAll(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM models WHERE id=:id LIMIT 1")
    suspend fun get(id: String): ModelConfigEntity?

    @Upsert
    suspend fun upsert(model: ModelConfigEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(models: List<ModelConfigEntity>)

    @Query("DELETE FROM models WHERE id=:id AND builtIn=0")
    suspend fun deleteCustom(id: String)
}
