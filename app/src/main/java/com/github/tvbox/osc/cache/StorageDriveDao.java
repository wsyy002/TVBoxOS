package com.github.tvbox.osc.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StorageDriveDao {

    @Query("SELECT * FROM storage_drive ORDER BY sortOrder ASC")
    List<StorageDrive> getAll();

    @Query("SELECT * FROM storage_drive WHERE id = :id")
    StorageDrive getById(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(StorageDrive drive);

    @Update
    void update(StorageDrive drive);

    @Delete
    void delete(StorageDrive drive);

    @Query("DELETE FROM storage_drive WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM storage_drive")
    void deleteAll();
}
