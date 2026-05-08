package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 驱动数据加载抽象基类
 */
public abstract class AbstractDriveViewModel {

    protected DriveFolderFile currentDrive;
    protected DriveFolderFile currentDriveNote;

    public interface LoadDataCallback {
        void callback(List<DriveFolderFile> items, boolean fromCache);
        void fail(String msg);
    }

    public abstract String loadData(LoadDataCallback callback);

    public void setCurrentDrive(DriveFolderFile drive) {
        this.currentDrive = drive;
        this.currentDriveNote = null;
    }

    public void setCurrentDriveNote(DriveFolderFile note) {
        this.currentDriveNote = note;
    }

    public DriveFolderFile getCurrentDrive() {
        return currentDrive;
    }

    public DriveFolderFile getCurrentDriveNote() {
        return currentDriveNote;
    }

    /**
     * 排序：文件夹在前，按名称排序
     */
    protected void sortData(List<DriveFolderFile> items) {
        Collections.sort(items, new Comparator<DriveFolderFile>() {
            final Collator collator = Collator.getInstance(Locale.CHINESE);

            @Override
            public int compare(DriveFolderFile o1, DriveFolderFile o2) {
                if (o1.isFile != o2.isFile) {
                    return o1.isFile ? 1 : -1;
                }
                if (o1.name == null) return -1;
                if (o2.name == null) return 1;
                return collator.compare(o1.name.toUpperCase(), o2.name.toUpperCase());
            }
        });
    }
}
