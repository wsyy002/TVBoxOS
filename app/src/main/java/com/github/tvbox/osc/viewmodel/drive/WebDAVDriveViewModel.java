package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WebDAV 文件浏览
 * 使用 sardine-android 库
 */
public class WebDAVDriveViewModel extends AbstractDriveViewModel {

    private Sardine webDAV;

    private boolean initWebDav() {
        if (webDAV != null) return true;
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);
            webDAV = new OkHttpSardine();
            if (config.has("username") && config.has("password")) {
                webDAV.setCredentials(
                        config.get("username").getAsString(),
                        config.get("password").getAsString()
                );
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private Sardine getWebDAV() {
        if (initWebDav()) return webDAV;
        return null;
    }

    private JsonObject parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(configJson).getAsJsonObject();
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = parseConfig(currentDrive.getDriveData().configJson);

        if (currentDriveNote == null) {
            String initPath = config.has("initPath") ? config.get("initPath").getAsString() : "";
            currentDriveNote = new DriveFolderFile(null, initPath, 0, false, null, null);
        }

        String targetPath = currentDriveNote.getAccessingPathStr() + (currentDriveNote.name != null ? currentDriveNote.name : "");

        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    Sardine webDAV = getWebDAV();
                    if (webDAV == null) {
                        if (callback != null) callback.fail("无法连接 WebDAV 服务器");
                        return;
                    }

                    try {
                        String baseUrl = config.get("url").getAsString();
                        if (!baseUrl.endsWith("/")) baseUrl += "/";

                        List<DavResource> files = webDAV.list(baseUrl + targetPath);
                        List<DriveFolderFile> items = new ArrayList<>();

                        for (DavResource file : files) {
                            String name = file.getName();
                            if (name == null || name.isEmpty() || name.equals("/")) continue;

                            int extIndex = name.lastIndexOf(".");
                            boolean isDir = file.isDirectory();
                            items.add(new DriveFolderFile(
                                    currentDriveNote,
                                    name, 0, !isDir,
                                    !isDir && extIndex >= 0 ? name.substring(extIndex + 1) : null,
                                    file.getModified() != null ? file.getModified().getTime() : 0
                            ));
                        }

                        sortData(items);

                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                        backItem.parentFolder = backItem;
                        items.add(0, backItem);

                        currentDriveNote.setChildren(items);
                        if (callback != null) callback.callback(items, false);
                    } catch (Exception e) {
                        if (callback != null) callback.fail("WebDAV 访问失败: " + e.getMessage());
                    }
                }
            }.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) callback.callback(currentDriveNote.getChildren(), true);
            return targetPath;
        }
    }
}
