package com.github.tvbox.osc.util;

import java.util.Locale;

/**
 * 存储驱动类型和工具方法
 */
public class StorageDriveType {

    public enum TYPE {
        LOCAL,
        WEBDAV,
        SMB,
        FTP
    }

    public static String[] getTypeNames() {
        return new String[]{
                "本地目录",
                "WebDAV",
                "SMB (网络共享)",
                "FTP"
        };
    }

    public static boolean isVideoType(String extension) {
        if (extension == null || extension.length() == 0)
            return false;
        extension = extension.toUpperCase(Locale.ROOT).trim();
        for (String videoType : VIDEO_TYPES) {
            if (videoType.equals(extension))
                return true;
        }
        return false;
    }

    private static final String[] VIDEO_TYPES = new String[]{
            "MP4", "MKV", "AVI", "MOV", "FLV", "WMV", "RMVB",
            "TS", "M2TS", "MTS", "VOB", "MPG", "MPEG",
            "M4V", "3GP", "3G2", "WEBM", "RM", "ASF",
            "OGM", "DIVX", "XVID", "ISO", "BDMV", "M3U8",
            "264", "H264", "H265", "HEVC", "VP9", "AV1"
    };
}
