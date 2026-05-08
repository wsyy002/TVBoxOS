package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.BaseConfiguration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.context.SingletonContext;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

/**
 * SMB (Samba/网络共享) 文件浏览
 * 使用 jcifs-ng 库，支持 SMB2/SMB3
 */
public class SMBDriveViewModel extends AbstractDriveViewModel {

    private CIFSContext smbContext;

    private CIFSContext getSmbContext() {
        if (smbContext != null) return smbContext;
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);

            String username = config.has("username") ? config.get("username").getAsString() : "";
            String password = config.has("password") ? config.get("password").getAsString() : "";
            String domain = config.has("domain") ? config.get("domain").getAsString() : "";

            CIFSContext baseCtx = SingletonContext.getInstance();
            if (!username.isEmpty()) {
                smbContext = baseCtx.withCredentials(
                        new jcifs.Credentials(domain, username, password.toCharArray())
                );
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
        if (configJson == null || configJson.isEmpty()) {
            return new JsonObject();
        }
        return JsonParser.parseString(configJson).getAsJsonObject();
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        String baseUrl = currentDrive.getDriveData().name; // smb://server/share/

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
                        SmbFile[] files = smbFile.listFiles(new SmbFileFilter() {
                            @Override
                            public boolean accept(SmbFile file) throws CIFSException {
                                return true; // 显示所有文件
                            }
                        });

                        List<DriveFolderFile> items = new ArrayList<>();
                        if (files != null) {
                            for (SmbFile file : files) {
                                String name = file.getName();
                                if (name == null) continue;
                                // 去除 SMB 路径末尾的 /
                                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                                // 去除开头路径
                                int lastSlash = name.lastIndexOf("/");
                                if (lastSlash >= 0) name = name.substring(lastSlash + 1);

                                int extIndex = name.lastIndexOf(".");
                                boolean isDir = file.isDirectory();
                                items.add(new DriveFolderFile(
                                        currentDriveNote,
                                        name, file.length(), !isDir,
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
