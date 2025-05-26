package com.bearmod.loader.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.bearmod.loader.database.dao.PatchDao;
import com.bearmod.loader.database.entity.PatchEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room database for the application
 */
@Database(entities = {PatchEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "bearloader_db";
    
    private static volatile AppDatabase instance;
    private static final int NUMBER_OF_THREADS = 4;
    
    // Executor service for database operations
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    // DAOs
    public abstract PatchDao patchDao();
    
    /**
     * Get database instance
     * @param context Application context
     * @return Database instance
     */
    public static AppDatabase getInstance(final Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME)
                            .addCallback(roomCallback)
                            .build();
                }
            }
        }
        return instance;
    }
    
    /**
     * Room callback for database creation and migration
     */
    private static final RoomDatabase.Callback roomCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            
            // Initialize database with default data if needed
            databaseWriteExecutor.execute(() -> {
                // This would be executed when the database is created for the first time
                // You can add initial data here if needed
            });
        }
        
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            
            // This would be executed when the database is opened
        }
    };
}
