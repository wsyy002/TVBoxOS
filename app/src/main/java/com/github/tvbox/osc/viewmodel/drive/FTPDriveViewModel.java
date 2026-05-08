package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.util.ArrayList;
import java.util.List;

/**
 * FTP 文件浏览
 * 使用 Apache Commons Net
 */
public class FTPDriveViewModel extends AbstractDriveViewModel {

    private FTPClient ftpClient;

    private FTPClient getFtpClient() {
        if (ftpClient != null && ftpClient.isConnected()) return ftpClient;
        try {
            JsonObject config = parseConfig(currentDrive.getDriveData().configJson);

            String host = config.get("host").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 21;
            String username = config.has("username") ? config.get("username").getAsString() : "anonymous";
            String password = config.has("password") ? config.get("password").getAsString() : "anonymous@";

            ftpClient = new FTPClient();
            ftpClient.connect(host, port);
            ftpClient.login(username, password);
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();

            return ftpClient;
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
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null, "/", 0, false, null, null);
        }

        String targetPath = currentDriveNote.getAccessingPathStr()
                + (currentDriveNote.name != null ? currentDriveNote.name : "");
        if (!targetPath.startsWith("/")) targetPath = "/" + targetPath;

        if (currentDriveNote.getChildren() == null) {
            final String path = targetPath;
            new Thread() {
                public void run() {
                    try {
                        FTPClient ftp = getFtpClient();
                        if (ftp == null) {
                            if (callback != null) callback.fail("无法连接 FTP 服务器");
                            return;
                        }

                        FTPFile[] files = ftp.listFiles(path);
                        List<DriveFolderFile> items = new ArrayList<>();

                        if (files != null) {
                            for (FTPFile file : files) {
                                String name = file.getName();
                                if (name == null || name.equals(".") || name.equals("..")) continue;

                                int extIndex = name.lastIndexOf(".");
                                boolean isDir = file.isDirectory();
                                items.add(new DriveFolderFile(
                                        currentDriveNote,
                                        name, file.getSize(), !isDir,
                                        !isDir && extIndex >= 0 ? name.substring(extIndex + 1) : null,
                                        file.getTimestamp() != null ? file.getTimestamp().getTimeInMillis() : 0
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
                        if (callback != null) callback.fail("FTP 访问失败: " + e.getMessage());
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
