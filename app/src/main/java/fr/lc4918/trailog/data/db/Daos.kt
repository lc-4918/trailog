package fr.lc4918.trailog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder, name") fun all(): Flow<List<FolderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(f: FolderEntity): Long
    @Update suspend fun update(f: FolderEntity)
    @Delete suspend fun delete(f: FolderEntity)
    @Query("UPDATE folders SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE folders SET parentId=:parentId WHERE id=:id") suspend fun move(id: Long, parentId: Long?)
    @Query("UPDATE folders SET sortOrder=:o WHERE id=:id") suspend fun setSort(id: Long, o: Int)
}

@Dao
interface LayerDao {
    @Query("SELECT * FROM layers ORDER BY name") fun all(): Flow<List<LayerEntity>>
    @Query("SELECT * FROM layers WHERE id=:id") suspend fun byId(id: Long): LayerEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(l: LayerEntity): Long
    @Update suspend fun update(l: LayerEntity)
    @Delete suspend fun delete(l: LayerEntity)
    @Query("UPDATE layers SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE layers SET folderId=:folderId WHERE id=:id") suspend fun move(id: Long, folderId: Long?)
    @Query("UPDATE layers SET schemaJson=:schema WHERE id=:id") suspend fun updateSchema(id: Long, schema: String)
    @Query("UPDATE layers SET visible=:v WHERE id=:id") suspend fun setVisible(id: Long, v: Boolean)
    @Query("UPDATE layers SET sortOrder=:o WHERE id=:id") suspend fun setSort(id: Long, o: Int)
    @Query("UPDATE layers SET color=:c WHERE id=:id") suspend fun setColor(id: Long, c: String)
    @Query("SELECT color FROM layers WHERE folderId IS :folderId") suspend fun colorsInFolder(folderId: Long?): List<String>
    @Query("SELECT COALESCE(MAX(sortOrder),0) FROM layers WHERE folderId IS :folderId") suspend fun maxSort(folderId: Long?): Int
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortOrder, name") fun all(): Flow<List<ProviderEntity>>
    @Query("SELECT * FROM providers WHERE id=:id") suspend fun byId(id: String): ProviderEntity?
    @Query("SELECT COUNT(*) FROM providers") suspend fun count(): Int
    @Upsert suspend fun upsert(p: ProviderEntity)
    @Upsert suspend fun upsertAll(list: List<ProviderEntity>)
    @Delete suspend fun delete(p: ProviderEntity)
    @Query("UPDATE providers SET folderId=:folderId WHERE id=:id") suspend fun move(id: String, folderId: Long?)
    @Query("UPDATE providers SET sortOrder=:o WHERE id=:id") suspend fun setSort(id: String, o: Int)
}

@Dao
interface CompositeDao {
    @Query("SELECT * FROM composites ORDER BY sortOrder, name") fun all(): Flow<List<CompositeEntity>>
    @Upsert suspend fun upsert(c: CompositeEntity)
    @Delete suspend fun delete(c: CompositeEntity)
    @Query("UPDATE composites SET folderId=:folderId WHERE id=:id") suspend fun move(id: Long, folderId: Long?)
    @Query("UPDATE composites SET sortOrder=:o WHERE id=:id") suspend fun setSort(id: Long, o: Int)
}

@Dao
interface BasemapFolderDao {
    @Query("SELECT * FROM basemap_folders ORDER BY sortOrder, name") fun all(): Flow<List<BasemapFolderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(f: BasemapFolderEntity): Long
    @Delete suspend fun delete(f: BasemapFolderEntity)
    @Query("UPDATE basemap_folders SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE basemap_folders SET parentId=:parentId WHERE id=:id") suspend fun move(id: Long, parentId: Long?)
    @Query("UPDATE basemap_folders SET sortOrder=:o WHERE id=:id") suspend fun setSort(id: Long, o: Int)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id=0") fun flow(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings WHERE id=0") suspend fun get(): SettingsEntity?
    @Upsert suspend fun upsert(s: SettingsEntity)
}
