package com.example.endlessfun;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ScoreDao {

    @Query("SELECT score FROM HighScore WHERE id = 1")
    Integer getHighScore();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveHighScore(HighScore highScore);
}
