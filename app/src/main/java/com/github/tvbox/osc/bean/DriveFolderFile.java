package com.github.tvbox.osc.bean;

import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.util.StorageDriveType;

import java.util.List;

/**
 * 驱动文件/文件夹条目
 */
public class DriveFolderFile {

    public String name;
    public long size;
    public boolean isFile;
    public String fileType;
    public long modifiedDate;
    public boolean isDelMode;

    public boolean isSelected;
    public DriveFolderFile parentFolder;
    private StorageDrive driveData;
    private List<DriveFolderFile> children;

    // 当前访问路径（相对）
    private String accessingPath;

    public DriveFolderFile(DriveFolderFile parentFolder, String name, long size, boolean isFile, String fileType, Long modifiedDate) {
        this.parentFolder = parentFolder;
        this.name = name;
        this.size = size;
        this.isFile = isFile;
        this.fileType = fileType;
        this.modifiedDate = modifiedDate != null ? modifiedDate : 0;
        this.isDelMode = false;
        this.isSelected = false;
        // 自动从父节点继承访问路径：父路径 + 父名称/  
        if (parentFolder != null) {
            String parentPath = parentFolder.getAccessingPathStr();
            String parentName = parentFolder.name;
            if (parentName != null && !parentName.isEmpty()) {
                this.accessingPath = parentPath + parentName + "/";
            } else {
                this.accessingPath = parentPath;
            }
        }
    }

    public DriveFolderFile(StorageDrive drive) {
        this.driveData = drive;
        this.name = drive.name;
        this.isFile = false;
        this.accessingPath = "";
    }

    public StorageDrive getDriveData() {
        return driveData;
    }

    public StorageDriveType.TYPE getDriveType() {
        // 遍历父链直到找到带 driveData 的根节点
        DriveFolderFile cur = this;
        while (cur != null) {
            if (cur.driveData != null) {
                return StorageDriveType.TYPE.values()[cur.driveData.driveType];
            }
            cur = cur.parentFolder;
        }
        return StorageDriveType.TYPE.LOCAL;
    }

    public String getAccessingPathStr() {
        return accessingPath != null ? accessingPath : "";
    }

    public void setAccessingPath(String path) {
        this.accessingPath = path;
    }

    public List<DriveFolderFile> getChildren() {
        return children;
    }

    public void setChildren(List<DriveFolderFile> children) {
        this.children = children;
    }
}
