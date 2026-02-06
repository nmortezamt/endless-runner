package com.example.endlessfun;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class HighScore {

    @PrimaryKey
    public int id = 1;

    public int score;
}
