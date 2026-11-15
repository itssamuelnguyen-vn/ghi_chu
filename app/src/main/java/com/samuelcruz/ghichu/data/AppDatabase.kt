package com.samuelcruz.ghichu.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    /** Every live note (trash and archive included; tombstones not). Screens filter in memory: a few thousand
     *  notes at most, so this is simpler and still fast. */
    @Query("SELECT * FROM note WHERE deleted = 0 AND template = 0")
    fun observeAll(): Flow<List<Note>>

    /** Templates (not in the trash), newest first. */
    @Query("SELECT * FROM note WHERE deleted = 0 AND template = 1 AND trashedAt IS NULL ORDER BY updatedAt DESC")
    fun observeTemplates(): Flow<List<Note>>

    @Query("SELECT * FROM note WHERE id = :id")
    fun observe(id: String): Flow<Note?>

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun get(id: String): Note?

    /** Notes and templates, trash included, tombstones not. */
    @Query("SELECT * FROM note WHERE deleted = 0")
    suspend fun allLive(): List<Note>

    /** Including tombstones (sync, backup). */
    @Query("SELECT * FROM note")
    suspend fun everything(): List<Note>

    @Query("SELECT * FROM note WHERE deleted = 0 AND reminderAt IS NOT NULL AND trashedAt IS NULL AND template = 0")
    suspend fun withReminders(): List<Note>

    @Query("SELECT * FROM note WHERE trashedAt IS NOT NULL AND trashedAt < :before AND deleted = 0")
    suspend fun trashedBefore(before: Long): List<Note>

    @Query("SELECT * FROM note WHERE labels LIKE '%,' || :labelId || ',%'")
    suspend fun withLabel(labelId: String): List<Note>

    @Query("SELECT COALESCE(MAX(position), 0) FROM note")
    suspend fun maxPosition(): Double

    @Query("SELECT COUNT(*) FROM note WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Upsert
    suspend fun upsert(note: Note)

    @Upsert
    suspend fun upsertAll(notes: List<Note>)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Query("UPDATE note SET remoteId = NULL, remoteVersion = 0, dirty = 1")
    suspend fun forgetRemote()
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM label WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM label WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Label>

    @Query("SELECT * FROM label")
    suspend fun everything(): List<Label>

    @Query("SELECT * FROM label WHERE id = :id")
    suspend fun get(id: String): Label?

    @Upsert
    suspend fun upsert(label: Label)

    @Query("SELECT COUNT(*) FROM label WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("UPDATE label SET remoteId = NULL, remoteVersion = 0, dirty = 1")
    suspend fun forgetRemote()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment ORDER BY createdAt")
    fun observeAll(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE noteId = :noteId ORDER BY createdAt")
    fun observeFor(noteId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun forNote(noteId: String): List<Attachment>

    @Query("SELECT * FROM attachment")
    suspend fun all(): List<Attachment>

    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun get(id: String): Attachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: Attachment)

    @Query("UPDATE attachment SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemote(id: String, remoteId: String?)

    @Query("UPDATE attachment SET noteId = :noteId WHERE id = :id")
    suspend fun move(id: String, noteId: String)

    @Query("DELETE FROM attachment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE attachment SET remoteId = NULL")
    suspend fun forgetRemote()
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revision WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun observeFor(noteId: String): Flow<List<Revision>>

    @Insert
    suspend fun insert(r: Revision)

    @Query(
        "DELETE FROM revision WHERE noteId = :noteId AND id NOT IN " +
            "(SELECT id FROM revision WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT ${Revision.MAX_PER_NOTE})",
    )
    suspend fun trim(noteId: String)

    @Query("SELECT * FROM revision WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT 1")
    suspend fun latest(noteId: String): Revision?

    @Query("DELETE FROM revision WHERE noteId = :noteId")
    suspend fun deleteFor(noteId: String)
}

/**
 * Version history (never destructive: these are real notes):
 * 1 → 2: `note.template` (note templates).
 * 2 → 3: `attachment.name` (any file attached to a note keeps its name).
 */
@Database(
    entities = [Note::class, Label::class, Attachment::class, Revision::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun labels(): LabelDao
    abstract fun attachments(): AttachmentDao
    abstract fun revisions(): RevisionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ghichu.db").build()
    }
}
