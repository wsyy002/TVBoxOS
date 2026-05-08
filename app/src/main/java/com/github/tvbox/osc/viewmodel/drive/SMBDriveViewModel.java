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
        String baseUrl = currentDrive.getDriveData().name;
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
