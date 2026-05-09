package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.SingletonContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

public class SMBDriveViewModel extends AbstractDriveViewModel {

    private CIFSContext smbContext;

    /**
     * 从 configJson 中提取 SMB 服务器地址
     * 兼容旧数据：如果 configJson 为空则回退到 drive.name
     */
    private String getSmbBaseUrl() {
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);
            // 中文标签 "服务器地址" 优先，其次 "主机地址"
            String url = null;
            if (config.has("服务器地址")) {
                url = config.get("服务器地址").getAsString();
            } else if (config.has("主机地址")) {
                url = "smb://" + config.get("主机地址").getAsString();
            }
            if (url != null) {
                // jcifs 需要服务器地址以 / 结尾
                if (!url.endsWith("/")) url += "/";
                return url;
            }
        } catch (Exception ignored) {}
        // 回退：旧数据直接把 URL 存在 name 里
        String fallback = currentDrive.getDriveData().name;
        if (!fallback.endsWith("/")) fallback += "/";
        return fallback;
    }

    private CIFSContext getSmbContext() {
        if (smbContext != null) return smbContext;
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);

            // 兼容中文和英文 key 名
            String username = "";
            if (config.has("用户名")) username = config.get("用户名").getAsString();
            else if (config.has("username")) username = config.get("username").getAsString();

            String password = "";
            if (config.has("密码")) password = config.get("密码").getAsString();
            else if (config.has("password")) password = config.get("password").getAsString();

            String domain = "WORKGROUP";
            if (config.has("域")) domain = config.get("域").getAsString();
            else if (config.has("domain")) domain = config.get("domain").getAsString();

            CIFSContext baseCtx = SingletonContext.getInstance();
            if (!username.isEmpty()) {
                smbContext = baseCtx.withCredentials(new jcifs.smb.NtlmPasswordAuthentication(
                        baseCtx, domain, username, password
                ));
            } else {
                smbContext = baseCtx;
            }
            return smbContext;
        } catch (Exception e) {
            android.util.Log.e("SMB", "getSmbContext error", e);
            e.printStackTrace();
        }
        return null;
    }

    private JsonObject parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) return new JsonObject();
        return JsonParser.parseString(configJson).getAsJsonObject();
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        // 从 configJson 提取服务器地址，而不是用 display name
        String baseUrl = getSmbBaseUrl();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null, "", 0, false, null, null);
        }

        if (currentDriveNote == null || currentDriveNote.name == null) {
            currentDriveNote = new DriveFolderFile(null, "", 0, false, null, null);
        }

        final String targetPath = baseUrl
                + (currentDriveNote.getAccessingPathStr() != null ? currentDriveNote.getAccessingPathStr() : "")
                + (currentDriveNote.name != null ? currentDriveNote.name : "");

        android.util.Log.i("SMB", "loadData targetPath: " + targetPath + ", baseUrl: " + baseUrl);

        if (currentDriveNote.getChildren() == null) {
            final String finalTargetPath = targetPath;
            new Thread() {
                public void run() {
                    try {
                        CIFSContext ctx = getSmbContext();
                        if (ctx == null) {
                            android.util.Log.e("SMB", "getSmbContext returned null");
                            if (callback != null) callback.fail("无法连接 SMB 服务器");
                            return;
                        }
                        SmbFile smbFile = new SmbFile(finalTargetPath, ctx);
                        android.util.Log.i("SMB_NAMES", "target=" + finalTargetPath);
                        SmbFile[] files = smbFile.listFiles();
                        List<DriveFolderFile> items = new ArrayList<>();
                        if (files != null) {
                            for (SmbFile file : files) {
                                // 使用 URL 提取名称：确保拿到纯净的叶子节点名称，避免 getName() 返回含父目录名
                                String urlStr = file.getURL().toString();
                                if (urlStr == null) continue;
                                // URL 最后是 /path/to/dir/ 或 /path/to/file
                                if (urlStr.endsWith("/")) urlStr = urlStr.substring(0, urlStr.length() - 1);
                                int lastSlash = urlStr.lastIndexOf("/");
                                String name = (lastSlash >= 0) ? urlStr.substring(lastSlash + 1) : urlStr;
                                if (name == null || name.isEmpty()) continue;
                                int extIndex = name.lastIndexOf(".");
                                boolean isDir = file.isDirectory();
                                android.util.Log.i("SMB_NAMES", "  url='" + file.getURL() + "' -> name='" + name + "' isDir=" + isDir);
                                items.add(new DriveFolderFile(
                                        currentDriveNote, name, file.length(), !isDir,
                                        !isDir && extIndex >= 0 ? name.substring(extIndex + 1) : null,
                                        file.lastModified()
                                ));
                            }
                        }
                        sortData(items);
                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                        backItem.parentFolder = backItem;
                        items.add(0, backItem);
                        currentDriveNote.setChildren(items);
                        if (callback != null) callback.callback(items, false);
                    } catch (Exception e) {
                        android.util.Log.e("SMB", "loadData error", e);
                        e.printStackTrace();
                        if (callback != null) callback.fail("SMB 访问失败: " + e.getMessage());
                    }
                }
            }.start();
            return finalTargetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) callback.callback(currentDriveNote.getChildren(), true);
            return targetPath;
        }
    }
}
