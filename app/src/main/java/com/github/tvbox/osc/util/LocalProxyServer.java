package com.github.tvbox.osc.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本地 HTTP 代理服务器
 * 为 SMB / FTP / WebDAV 等不支持直接流式播放的协议提供 HTTP 中转
 * 播放器请求 http://127.0.0.1:9978/stream/{id}，代理读取目标文件流
 */
public class LocalProxyServer extends NanoHTTPD {

    private static final String TAG = "LocalProxyServer";
    private static final int PORT = 19978;
    private static LocalProxyServer instance;

    // 存储代理流映射
    private final ConcurrentHashMap<String, StreamSource> streamMap = new ConcurrentHashMap<>();

    private LocalProxyServer() {
        super(PORT);
    }

    public static synchronized LocalProxyServer getInstance() {
        if (instance == null) {
            instance = new LocalProxyServer();
            try {
                instance.start();
                Log.i(TAG, "Proxy server started on port " + PORT);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start proxy server", e);
            }
        }
        return instance;
    }

    /**
     * 注册一个流式文件，返回代理 URL
     */
    public String registerStream(String filePath, StreamSource source) {
        String id = String.valueOf(System.currentTimeMillis()) + "_" + filePath.hashCode();
        streamMap.put(id, source);
        return "http://127.0.0.1:" + PORT + "/stream/" + id;
    }

    /**
     * 为 SMB 路径创建代理 URL（带认证信息）
     */
    public String registerSmbStream(String smbUrl, final String username, final String password, final String domain) {
        final long[] knownSize = {-1};
        StreamSource source = new StreamSource() {
            @Override
            public InputStream openStream() throws Exception {
                Log.i(TAG, "SMB openStream START: " + smbUrl);
                long t0 = System.currentTimeMillis();
                try {
                    Log.i(TAG, "SMB step1: loading jcifs classes");
                    Class<?> smbFileClass = Class.forName("jcifs.smb.SmbFile");
                    Class<?> authClass = Class.forName("jcifs.smb.NtlmPasswordAuthentication");
                    Class<?> ctxClass = Class.forName("jcifs.CIFSContext");
                    Class<?> ctxSingleClass = Class.forName("jcifs.context.SingletonContext");
                    Log.i(TAG, "SMB step1 OK");

                    Log.i(TAG, "SMB step2: get SingletonContext");
                    Object baseCtx = ctxSingleClass.getMethod("getInstance").invoke(null);
                    Log.i(TAG, "SMB step2 OK");

                    if (username != null && !username.isEmpty()) {
                        Log.i(TAG, "SMB step3: NtlmPasswordAuth user=" + username + " domain=" + domain);
                        java.lang.reflect.Constructor<?> authCtor = authClass.getConstructor(ctxClass, String.class, String.class, String.class);
                        String dom = (domain != null && !domain.isEmpty()) ? domain : "WORKGROUP";
                        Object auth = authCtor.newInstance(baseCtx, dom, username, password);
                        Class<?> credsClass = Class.forName("jcifs.Credentials");
                        java.lang.reflect.Method withCreds = ctxClass.getMethod("withCredentials", credsClass);
                        baseCtx = withCreds.invoke(baseCtx, auth);
                        Log.i(TAG, "SMB step3 OK");
                    }

                    Log.i(TAG, "SMB step4: creating SmbFile");
                    java.lang.reflect.Constructor<?> fileCtor = smbFileClass.getConstructor(String.class, ctxClass);
                    Object smbFile = fileCtor.newInstance(smbUrl, baseCtx);
                    Log.i(TAG, "SMB step4 OK");

                    Log.i(TAG, "SMB step5: getInputStream");
                    java.lang.reflect.Method getInputStream = smbFileClass.getMethod("getInputStream");
                    long t1 = System.currentTimeMillis();
                    InputStream result = (InputStream) getInputStream.invoke(smbFile);
                    long t2 = System.currentTimeMillis();
                    Log.i(TAG, "SMB step5: invoke time=" + (t2 - t1) + "ms");
                    if (result != null) {
                        result = new java.io.BufferedInputStream(result, 1024 * 256);
                    }
                    Log.i(TAG, "SMB step5 OK: stream=" + (result != null) + " total=" + (t2-t0) + "ms");
                    return result;
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    Log.e(TAG, "SMB FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage() + " time=" + (System.currentTimeMillis()-t0) + "ms");
                    throw e;
                }
            }

            @Override
            public long getSize() {
                return knownSize[0];
            }

            @Override
            public String getMimeType() {
                return mimeForPath(smbUrl);
            }
        };
        return registerStream(smbUrl, source);
    }

    /**
     * 为 SMB/FTP 路径创建代理 URL
     */
    public String proxyUrl(String originalPath) {
        // 解析路径创建流源
        StreamSource source = new StreamSource() {
            @Override
            public InputStream openStream() throws Exception {
                if (originalPath.startsWith("smb://")) {
                    return openSmbStream(originalPath);
                } else if (originalPath.startsWith("ftp://") || originalPath.startsWith("ftps://")) {
                    return openFtpStream(originalPath);
                } else if (originalPath.startsWith("file://") || originalPath.startsWith("/")) {
                    return new FileInputStream(new File(originalPath));
                }
                throw new UnsupportedOperationException("Unsupported protocol: " + originalPath);
            }

            @Override
            public long getSize() {
                return -1;
            }

            @Override
            public String getMimeType() {
                return mimeForPath(originalPath);
            }
        };

        return registerStream(originalPath, source);
    }

    private String mimeForPath(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".wmv")) return "video/x-ms-wmv";
        if (lower.endsWith(".m3u8")) return "application/x-mpegURL";
        return "video/mp4";
    }

    /**
     * 打开 SMB 流
     */
    private InputStream openSmbStream(String smbUrl) throws Exception {
        // 使用 JCIFS-NG
        try {
            // 尝试反射调用，避免编译时依赖问题
            Class<?> smbFileClass = Class.forName("jcifs.smb.SmbFile");
            java.lang.reflect.Constructor<?> ctor = smbFileClass.getConstructor(String.class);
            Object smbFile = ctor.newInstance(smbUrl);
            java.lang.reflect.Method getInputStream = smbFileClass.getMethod("getInputStream");
            return (InputStream) getInputStream.invoke(smbFile);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "JCIFS not available for SMB: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 打开 FTP 流
     */
    private InputStream openFtpStream(String ftpUrl) throws Exception {
        // 使用 Apache Commons Net
        try {
            java.net.URL url = new java.net.URL(ftpUrl);
            String host = url.getHost();
            int port = url.getPort() > 0 ? url.getPort() : 21;
            String userInfo = url.getUserInfo();
            String username = "anonymous";
            String password = "anonymous@";
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            }
            String path = url.getPath();

            org.apache.commons.net.ftp.FTPClient ftp = new org.apache.commons.net.ftp.FTPClient();
            ftp.connect(host, port);
            ftp.login(username, password);
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();

            return new FTPInputStreamWrapper(ftp, ftp.retrieveFileStream(path));
        } catch (Exception e) {
            Log.e(TAG, "FTP connection failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Log.i(TAG, "serve REQUEST: uri=" + uri + " method=" + session.getMethod());

        if (uri.startsWith("/stream/")) {
            String id = uri.substring(8);
            StreamSource source = streamMap.get(id);

            if (source == null) {
                return newFixedLengthResponse(
                        Response.Status.NOT_FOUND, "text/plain",
                        "Stream not found: " + id
                );
            }

            try {
                // 支持断点续传（Range 请求）
                String rangeHeader = session.getHeaders().get("range");
                InputStream inputStream = source.openStream();
                long fileSize = source.getSize();

                if (rangeHeader != null) {
                    // 有 Range 头，尝试跳到指定位置（支持拖动进度条、快进等操作）
                    String[] range = rangeHeader.replace("bytes=", "").split("-");
                    long start = Long.parseLong(range[0]);
                    long end = (fileSize > 0 && range.length > 1) ? Long.parseLong(range[1]) : (fileSize > 0 ? fileSize - 1 : -1);
                    long length = (end > 0 && fileSize > 0) ? end - start + 1 : -1;

                    // 跳过到指定位置
                    long skipped = 0;
                    while (skipped < start) {
                        long s = inputStream.skip(start - skipped);
                        if (s <= 0) break;
                        skipped += s;
                    }

                    Response response = newChunkedResponse(
                            Response.Status.PARTIAL_CONTENT,
                            source.getMimeType(),
                            inputStream
                    );
                    response.addHeader("Accept-Ranges", "bytes");
                    if (fileSize > 0) {
                        end = (end > 0) ? end : fileSize - 1;
                        length = end - start + 1;
                        response.addHeader("Content-Range",
                                "bytes " + start + "-" + end + "/" + fileSize);
                        response.addHeader("Content-Length", String.valueOf(length));
                    }
                    return response;
                } else {
                    // 无 Range，全量分块传输
                    Response response = newChunkedResponse(
                            Response.Status.OK,
                            source.getMimeType(),
                            inputStream
                    );
                    response.addHeader("Accept-Ranges", "bytes");
                    if (fileSize > 0) {
                        response.addHeader("Content-Length", String.valueOf(fileSize));
                    }
                    return response;
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream error: " + e.getMessage());
                return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, "text/plain",
                        "Stream error: " + e.getMessage()
                );
            }
        }

        return newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "Not found"
        );
    }

    /**
     * 流源接口
     */
    public interface StreamSource {
        InputStream openStream() throws Exception;
        long getSize();
        String getMimeType();
    }

    /**
     * FTP 流包装器，确保释放时关闭 FTP 连接
     */
    private static class FTPInputStreamWrapper extends InputStream {
        private final org.apache.commons.net.ftp.FTPClient ftp;
        private final InputStream wrapped;

        FTPInputStreamWrapper(org.apache.commons.net.ftp.FTPClient ftp, InputStream wrapped) {
            this.ftp = ftp;
            this.wrapped = wrapped;
        }

        @Override
        public int read() throws java.io.IOException {
            return wrapped.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            return wrapped.read(b, off, len);
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                wrapped.close();
            } finally {
                ftp.logout();
                ftp.disconnect();
            }
        }
    }
}
