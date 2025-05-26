package com.bearmod.loader.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bearmod.loader.database.AppDatabase;
import com.bearmod.loader.database.dao.PatchDao;
import com.bearmod.loader.database.entity.PatchEntity;
import com.bearmod.loader.model.Patch;
import com.bearmod.loader.network.NetworkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Repository for patch data
 * Handles data operations between the database and network
 */
public class PatchRepository {
    
    private static final String TAG = "PatchRepository";
    
    private static PatchRepository instance;
    private final PatchDao patchDao;
    private final NetworkManager networkManager;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    /**
     * Private constructor to enforce singleton pattern
     * @param context Application context
     */
    private PatchRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        patchDao = database.patchDao();
        networkManager = NetworkManager.getInstance(context);
    }
    
    /**
     * Get repository instance
     * @param context Application context
     * @return Repository instance
     */
    public static synchronized PatchRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PatchRepository(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Get all patches
     * @return LiveData list of all patches
     */
    public LiveData<List<Patch>> getAllPatches() {
        return Transformations.map(patchDao.getAllPatchesLive(),
                entities -> entities.stream()
                        .map(PatchEntity::toPatch)
                        .collect(Collectors.toList()));
    }
    
    /**
     * Get patch by ID
     * @param id Patch ID
     * @return LiveData patch with the given ID
     */
    public LiveData<Patch> getPatchById(String id) {
        return Transformations.map(patchDao.getPatchByIdLive(id), PatchEntity::toPatch);
    }
    
    /**
     * Get patches by game version
     * @param gameVersion Game version
     * @return LiveData list of patches for the given game version
     */
    public LiveData<List<Patch>> getPatchesByGameVersion(String gameVersion) {
        return Transformations.map(patchDao.getPatchesByGameVersionLive(gameVersion),
                entities -> entities.stream()
                        .map(PatchEntity::toPatch)
                        .collect(Collectors.toList()));
    }
    
    /**
     * Get latest patches for a specific game version
     * @param gameVersion Game version
     * @param callback Callback for result
     */
    public void getLatestPatches(String gameVersion, PatchCallback callback) {
        executor.execute(() -> {
            try {
                List<PatchEntity> entities = patchDao.getLatestPatches(gameVersion);
                List<Patch> patches = entities.stream()
                        .map(PatchEntity::toPatch)
                        .collect(Collectors.toList());
                callback.onSuccess(patches);
            } catch (Exception e) {
                Log.e(TAG, "Error getting latest patches: " + e.getMessage());
                callback.onError("Error getting latest patches: " + e.getMessage());
            }
        });
    }
    
    /**
     * Sync patches from network
     * @param callback Callback for result
     */
    public void syncPatches(PatchCallback callback) {
        // TODO: Implement actual network sync
        // This is a mock implementation for demonstration
        
        executor.execute(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1000);
                
                // Create mock patches
                List<Patch> patches = createMockPatches();
                
                // Save to database
                List<PatchEntity> entities = patches.stream()
                        .map(PatchEntity::new)
                        .collect(Collectors.toList());
                
                patchDao.insertAll(entities);
                
                // Return success
                callback.onSuccess(patches);
            } catch (Exception e) {
                Log.e(TAG, "Error syncing patches: " + e.getMessage());
                callback.onError("Error syncing patches: " + e.getMessage());
            }
        });
    }
    
    /**
     * Save patch
     * @param patch Patch to save
     * @param callback Callback for result
     */
    public void savePatch(Patch patch, PatchCallback callback) {
        executor.execute(() -> {
            try {
                PatchEntity entity = new PatchEntity(patch);
                patchDao.insert(entity);
                callback.onSuccess(List.of(patch));
            } catch (Exception e) {
                Log.e(TAG, "Error saving patch: " + e.getMessage());
                callback.onError("Error saving patch: " + e.getMessage());
            }
        });
    }
    
    /**
     * Delete patch
     * @param patch Patch to delete
     * @param callback Callback for result
     */
    public void deletePatch(Patch patch, PatchCallback callback) {
        executor.execute(() -> {
            try {
                PatchEntity entity = new PatchEntity(patch);
                patchDao.delete(entity);
                callback.onSuccess(List.of(patch));
            } catch (Exception e) {
                Log.e(TAG, "Error deleting patch: " + e.getMessage());
                callback.onError("Error deleting patch: " + e.getMessage());
            }
        });
    }
    
    /**
     * Create mock patches for demonstration
     * @return List of mock patches
     */
    private List<Patch> createMockPatches() {
        List<Patch> patches = new ArrayList<>();
        
        // Add mock patches
        patches.add(new Patch(
                "1",
                "Memory Patch v1.2",
                "This patch modifies memory values to enhance gameplay",
                "1.0.5",
                "2023-06-15",
                Patch.PatchStatus.UP_TO_DATE
        ));
        
        patches.add(new Patch(
                "2",
                "Speed Hack v2.0",
                "Increases movement speed and reduces cooldowns",
                "1.0.5",
                "2023-06-10",
                Patch.PatchStatus.UPDATE_AVAILABLE
        ));
        
        patches.add(new Patch(
                "3",
                "Resource Modifier v1.5",
                "Modifies resource generation and collection rates",
                "1.0.4",
                "2023-05-28",
                Patch.PatchStatus.NOT_INSTALLED
        ));
        
        return patches;
    }
    
    /**
     * Patch callback interface
     */
    public interface PatchCallback {
        void onSuccess(List<Patch> patches);
        void onError(String error);
    }
}
