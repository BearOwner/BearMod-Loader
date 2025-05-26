package com.bearmod.loader.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.bearmod.loader.model.Patch;

/**
 * Patch entity for Room database
 */
@Entity(tableName = "patches")
public class PatchEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    private String name;
    private String description;
    private String gameVersion;
    private String updateDate;
    private String status;
    private String downloadUrl;
    private String localPath;
    private boolean isInstalled;
    private long lastUpdated;
    
    /**
     * Default constructor for Room
     */
    public PatchEntity() {
    }
    
    /**
     * Constructor from Patch model
     * @param patch Patch model
     */
    public PatchEntity(Patch patch) {
        this.id = patch.getId();
        this.name = patch.getName();
        this.description = patch.getDescription();
        this.gameVersion = patch.getGameVersion();
        this.updateDate = patch.getUpdateDate();
        this.status = patch.getStatus().name();
        this.lastUpdated = System.currentTimeMillis();
        
        // Default values
        this.isInstalled = patch.getStatus() == Patch.PatchStatus.UP_TO_DATE;
    }
    
    /**
     * Convert to Patch model
     * @return Patch model
     */
    public Patch toPatch() {
        Patch.PatchStatus patchStatus;
        try {
            patchStatus = Patch.PatchStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            patchStatus = Patch.PatchStatus.NOT_INSTALLED;
        }
        
        return new Patch(id, name, description, gameVersion, updateDate, patchStatus);
    }
    
    @NonNull
    public String getId() {
        return id;
    }
    
    public void setId(@NonNull String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getGameVersion() {
        return gameVersion;
    }
    
    public void setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
    }
    
    public String getUpdateDate() {
        return updateDate;
    }
    
    public void setUpdateDate(String updateDate) {
        this.updateDate = updateDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    public String getLocalPath() {
        return localPath;
    }
    
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
    
    public boolean isInstalled() {
        return isInstalled;
    }
    
    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
