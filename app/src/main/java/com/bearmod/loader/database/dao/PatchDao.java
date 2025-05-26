package com.bearmod.loader.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.bearmod.loader.database.entity.PatchEntity;

import java.util.List;

/**
 * Data Access Object for Patch entities
 */
@Dao
public interface PatchDao {
    
    /**
     * Insert a patch
     * @param patch Patch to insert
     * @return Row ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PatchEntity patch);
    
    /**
     * Insert multiple patches
     * @param patches Patches to insert
     * @return Row IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<PatchEntity> patches);
    
    /**
     * Update a patch
     * @param patch Patch to update
     */
    @Update
    void update(PatchEntity patch);
    
    /**
     * Delete a patch
     * @param patch Patch to delete
     */
    @Delete
    void delete(PatchEntity patch);
    
    /**
     * Get all patches
     * @return List of all patches
     */
    @Query("SELECT * FROM patches ORDER BY name ASC")
    List<PatchEntity> getAllPatches();
    
    /**
     * Get all patches as LiveData
     * @return LiveData list of all patches
     */
    @Query("SELECT * FROM patches ORDER BY name ASC")
    LiveData<List<PatchEntity>> getAllPatchesLive();
    
    /**
     * Get patch by ID
     * @param id Patch ID
     * @return Patch with the given ID
     */
    @Query("SELECT * FROM patches WHERE id = :id")
    PatchEntity getPatchById(String id);
    
    /**
     * Get patch by ID as LiveData
     * @param id Patch ID
     * @return LiveData patch with the given ID
     */
    @Query("SELECT * FROM patches WHERE id = :id")
    LiveData<PatchEntity> getPatchByIdLive(String id);
    
    /**
     * Get patches by game version
     * @param gameVersion Game version
     * @return List of patches for the given game version
     */
    @Query("SELECT * FROM patches WHERE gameVersion = :gameVersion ORDER BY name ASC")
    List<PatchEntity> getPatchesByGameVersion(String gameVersion);
    
    /**
     * Get patches by game version as LiveData
     * @param gameVersion Game version
     * @return LiveData list of patches for the given game version
     */
    @Query("SELECT * FROM patches WHERE gameVersion = :gameVersion ORDER BY name ASC")
    LiveData<List<PatchEntity>> getPatchesByGameVersionLive(String gameVersion);
    
    /**
     * Get installed patches
     * @return List of installed patches
     */
    @Query("SELECT * FROM patches WHERE isInstalled = 1 ORDER BY name ASC")
    List<PatchEntity> getInstalledPatches();
    
    /**
     * Get installed patches as LiveData
     * @return LiveData list of installed patches
     */
    @Query("SELECT * FROM patches WHERE isInstalled = 1 ORDER BY name ASC")
    LiveData<List<PatchEntity>> getInstalledPatchesLive();
    
    /**
     * Get patches by status
     * @param status Patch status
     * @return List of patches with the given status
     */
    @Query("SELECT * FROM patches WHERE status = :status ORDER BY name ASC")
    List<PatchEntity> getPatchesByStatus(String status);
    
    /**
     * Get patches by status as LiveData
     * @param status Patch status
     * @return LiveData list of patches with the given status
     */
    @Query("SELECT * FROM patches WHERE status = :status ORDER BY name ASC")
    LiveData<List<PatchEntity>> getPatchesByStatusLive(String status);
    
    /**
     * Get latest patches for a specific game version
     * @param gameVersion Game version
     * @return List of patches for the given game version
     */
    @Query("SELECT * FROM patches WHERE gameVersion = :gameVersion ORDER BY lastUpdated DESC LIMIT 10")
    List<PatchEntity> getLatestPatches(String gameVersion);
    
    /**
     * Delete all patches
     */
    @Query("DELETE FROM patches")
    void deleteAll();
    
    /**
     * Count patches
     * @return Number of patches
     */
    @Query("SELECT COUNT(*) FROM patches")
    int count();
}
