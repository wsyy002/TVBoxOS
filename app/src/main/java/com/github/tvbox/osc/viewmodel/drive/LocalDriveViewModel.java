package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地文件浏览
 */
public class LocalDriveViewModel extends AbstractDriveViewModel {

    /**
     * 从 configJson 或 name 中提取本地目录根路径
     */
    private String getLocalRootPath() {
        try {
            String configJson = currentDrive.getDriveData().configJson;
            if (configJson != null && !configJson.isEmpty()) {
                com.google.gson.JsonObject cfg = com.google.gson.JsonParser.parseString(configJson).getAsJsonObject();
                if (cfg.has("路径")) {
                    String p = cfg.get("路径").getAsString();
                    if (p != null && !p.isEmpty()) return p;
                }
            }
        } catch (Exception ignored) {}
        // 回退：旧数据直接把路径存在 name 里
        return currentDrive.getDriveData().name;
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null, "", 0, false, null, null);
        }

        // 从 configJson 提取路径，如果没有则回退到 drive.name
        String rootPath = getLocalRootPath();
        String path = rootPath + currentDriveNote.getAccessingPathStr() + (currentDriveNote.name != null ? currentDriveNote.name : "");

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
