package models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "patterns")
public class Pattern {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String displayName;
}
