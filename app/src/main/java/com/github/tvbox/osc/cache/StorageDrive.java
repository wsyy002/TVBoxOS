package com.github.tvbox.osc.cache;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 存储驱动配置（本地目录 / WebDAV / SMB / FTP）
 */
@Entity(tableName = "storage_drive")
public class StorageDrive {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** 显示名称 */
    public String name;

    /** 驱动类型: 0=LOCAL, 1=WEBDAV, 2=SMB, 3=FTP */
    public int driveType;

    /** 配置 JSON（URL, 用户名, 密码等） */
    public String configJson;

    /** 排序 */
    public int sortOrder;

    public StorageDrive() {
    }

    public StorageDrive(String name, int driveType, String configJson) {
        this.name = name;
        this.driveType = driveType;
        this.configJson = configJson;
        this.sortOrder = 0;
    }
}
