package com.bearmod.loader.model;

/**
 * Patch release model
 * Represents a patch release from GitHub or cloud storage
 */
public class PatchRelease {
    
    private String id;
    private String name;
    private String description;
    private String gameVersion;
    private String releaseDate;
    private String downloadUrl;
    private double apkSizeMB;
    private double obbSizeMB;
    
    /**
     * Constructor
     * @param id Release ID
     * @param name Release name
     * @param description Release description
     * @param gameVersion Game version
     * @param releaseDate Release date
     * @param downloadUrl Download URL
     * @param apkSizeMB APK size in MB
     * @param obbSizeMB OBB size in MB
     */
    public PatchRelease(String id, String name, String description, String gameVersion, 
                        String releaseDate, String downloadUrl, double apkSizeMB, double obbSizeMB) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.gameVersion = gameVersion;
        this.releaseDate = releaseDate;
        this.downloadUrl = downloadUrl;
        this.apkSizeMB = apkSizeMB;
        this.obbSizeMB = obbSizeMB;
    }
    
    /**
     * Get release ID
     * @return Release ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Set release ID
     * @param id Release ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Get release name
     * @return Release name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Set release name
     * @param name Release name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Get release description
     * @return Release description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Set release description
     * @param description Release description
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Get game version
     * @return Game version
     */
    public String getGameVersion() {
        return gameVersion;
    }
    
    /**
     * Set game version
     * @param gameVersion Game version
     */
    public void setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
    }
    
    /**
     * Get release date
     * @return Release date
     */
    public String getReleaseDate() {
        return releaseDate;
    }
    
    /**
     * Set release date
     * @param releaseDate Release date
     */
    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }
    
    /**
     * Get download URL
     * @return Download URL
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    /**
     * Set download URL
     * @param downloadUrl Download URL
     */
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    /**
     * Get APK size in MB
     * @return APK size in MB
     */
    public double getApkSizeMB() {
        return apkSizeMB;
    }
    
    /**
     * Set APK size in MB
     * @param apkSizeMB APK size in MB
     */
    public void setApkSizeMB(double apkSizeMB) {
        this.apkSizeMB = apkSizeMB;
    }
    
    /**
     * Get OBB size in MB
     * @return OBB size in MB
     */
    public double getObbSizeMB() {
        return obbSizeMB;
    }
    
    /**
     * Set OBB size in MB
     * @param obbSizeMB OBB size in MB
     */
    public void setObbSizeMB(double obbSizeMB) {
        this.obbSizeMB = obbSizeMB;
    }
    
    /**
     * Get total size in MB
     * @return Total size in MB
     */
    public double getTotalSizeMB() {
        return apkSizeMB + obbSizeMB;
    }
}
