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
            if (config.has("服务器地址")) {
                return config.get("服务器地址").getAsString();
            }
            if (config.has("主机地址")) {
                return "smb://" + config.get("主机地址").getAsString();
            }
        } catch (Exception ignored) {}
        // 回退：旧数据直接把 URL 存在 name 里
        return currentDrive.getDriveData().name;
    }

    private CIFSContext getSmbContext() {
        if (smbContext != null) return smbContext;
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);

            String username = config.has("username") ? config.get("username").getAsString() : "";
            String password = config.has("password") ? config.get("password").getAsString() : "";

            CIFSContext baseCtx = SingletonContext.getInstance();
            if (!username.isEmpty()) {
                // jcifs-ng: NtlmPasswordAuthentication is the concrete Credentials implementation
                String domain = config.has("domain") ? config.get("domain").getAsString() : "";
                // jcifs-ng 2.1.7: NtlmPasswordAuthentication(CIFSContext, String domain, String username, String password)
                smbContext = baseCtx.withCredentials(new jcifs.smb.NtlmPasswordAuthentication(
                        baseCtx, domain, username, password
                ));
            } else {
                smbContext = baseCtx;
            }
            return smbContext;
        } catch (Exception e) {
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

        final String targetPath = baseUrl
                + currentDriveNote.getAccessingPathStr()
                + (currentDriveNote.name != null ? currentDriveNote.name : "");

        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    try {
                        CIFSContext ctx = getSmbContext();
                        if (ctx == null) {
                            if (callback != null) callback.fail("无法连接 SMB 服务器");
                            return;
                        }
                        SmbFile smbFile = new SmbFile(targetPath, ctx);
                        SmbFile[] files = smbFile.listFiles();
                        List<DriveFolderFile> items = new ArrayList<>();
                        if (files != null) {
                            for (SmbFile file : files) {
                                String name = file.getName();
                                if (name == null) continue;
                                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                                int lastSlash = name.lastIndexOf("/");
                                if (lastSlash >= 0) name = name.substring(lastSlash + 1);
                                int extIndex = name.lastIndexOf(".");
                                boolean isDir = file.isDirectory();
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
                        if (callback != null) callback.fail("SMB 访问失败: " + e.getMessage());
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
