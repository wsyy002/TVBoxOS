package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地文件浏览
 */
public class LocalDriveViewModel extends AbstractDriveViewModel {

    @Override
    public String loadData(LoadDataCallback callback) {
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null, "", 0, false, null, null);
        }

        String path = currentDrive.name + currentDriveNote.getAccessingPathStr() + (currentDriveNote.name != null ? currentDriveNote.name : "");

        if (currentDriveNote.getChildren() == null) {
            File[] files = (new File(path)).listFiles();
            List<DriveFolderFile> items = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    int extIndex = file.getName().lastIndexOf(".");
                    items.add(new DriveFolderFile(
                            currentDriveNote,
                            file.getName(),
                            file.length(),
                            file.isFile(),
                            file.isFile() && extIndex >= 0 ? file.getName().substring(extIndex + 1) : null,
                            file.lastModified()
                    ));
                }
            }

            sortData(items);

            DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
            backItem.parentFolder = backItem;
            items.add(0, backItem);

            currentDriveNote.setChildren(items);

            if (callback != null) {
                callback.callback(currentDriveNote.getChildren(), false);
            }
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) {
                callback.callback(currentDriveNote.getChildren(), true);
            }
        }

        return path;
    }
}
