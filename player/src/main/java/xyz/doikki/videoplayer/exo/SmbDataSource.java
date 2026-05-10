package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

/**
 * 直接从 SMB 文件读取的 DataSource，绕过 HTTP 代理
 * 支持 Range 请求（拖动进度条）
 */
public class SmbDataSource implements DataSource {

    private static final String TAG = "SmbDataSource";
    private static final int BUFFER_SIZE = 1024 * 256; // 256KB

    private static final ConcurrentHashMap<String, Object> ctxCache = new ConcurrentHashMap<>();

    private Uri uri;
    private InputStream inputStream;
    private long bytesRemaining;
    private long assetLength = -1;

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.uri = dataSpec.uri;
        long position = dataSpec.position;
        long length = dataSpec.length;

        Log.i(TAG, "open: url=" + uri + " pos=" + position + " len=" + length);

        try {
            String userInfo = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            String smbUrl = "smb://" + host + (port > 0 ? ":" + port : "") + path;

            String username = "", password = "", domain = "WORKGROUP";
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = java.net.URLDecoder.decode(parts[0], "UTF-8");
                password = java.net.URLDecoder.decode(parts[1], "UTF-8");
            } else if (userInfo != null) {
                username = java.net.URLDecoder.decode(userInfo, "UTF-8");
            }
            if (uri.getFragment() != null) {
                domain = java.net.URLDecoder.decode(uri.getFragment(), "UTF-8");
            }

            Class<?> smbFileClass = Class.forName("jcifs.smb.SmbFile");
            Class<?> authClass = Class.forName("jcifs.smb.NtlmPasswordAuthentication");
            Class<?> ctxClass = Class.forName("jcifs.CIFSContext");
            Class<?> ctxSingleClass = Class.forName("jcifs.context.SingletonContext");

            Object baseCtx = ctxSingleClass.getMethod("getInstance").invoke(null);

            String ctxKey = username + "@" + domain;
            if (!username.isEmpty()) {
                Object cached = ctxCache.get(ctxKey);
                if (cached != null) {
                    baseCtx = cached;
                } else {
                    java.lang.reflect.Constructor<?> authCtor = authClass.getConstructor(ctxClass, String.class, String.class, String.class);
                    Object auth = authCtor.newInstance(baseCtx, domain, username, password);
                    Class<?> credsClass = Class.forName("jcifs.Credentials");
                    java.lang.reflect.Method withCreds = ctxClass.getMethod("withCredentials", credsClass);
                    baseCtx = withCreds.invoke(baseCtx, auth);
                    ctxCache.put(ctxKey, baseCtx);
                }
            }

            java.lang.reflect.Constructor<?> fileCtor = smbFileClass.getConstructor(String.class, ctxClass);
            Object smbFile = fileCtor.newInstance(smbUrl, baseCtx);

            try {
                java.lang.reflect.Method lengthMethod = smbFileClass.getMethod("length");
                Object len = lengthMethod.invoke(smbFile);
                if (len instanceof Long) assetLength = (Long) len;
            } catch (Exception ignored) {}

            java.lang.reflect.Method getInputStream = smbFileClass.getMethod("getInputStream");
            InputStream rawStream = (InputStream) getInputStream.invoke(smbFile);
            if (rawStream == null) throw new IOException("Failed to open SMB stream");
            inputStream = new java.io.BufferedInputStream(rawStream, BUFFER_SIZE);

            if (position > 0) {
                long skipped = 0;
                while (skipped < position) {
                    long s = inputStream.skip(position - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }
            }

            if (length != -1) {
                bytesRemaining = length;
            } else if (assetLength != -1) {
                bytesRemaining = assetLength - position;
            } else {
                bytesRemaining = -1;
            }

            Log.i(TAG, "open OK: assetLength=" + assetLength + " position=" + position);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("SMB open failed: " + e.getMessage(), e);
        }

        return assetLength;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) return 0;
        if (bytesRemaining != -1 && bytesRemaining <= 0) return -1;

        int bytesRead;
        try {
            bytesRead = inputStream.read(buffer, offset, readLength);
        } catch (IOException e) {
            throw new IOException("SMB read error", e);
        }

        if (bytesRead == -1) return -1;

        if (bytesRemaining != -1) {
            bytesRemaining -= bytesRead;
        }
        return bytesRead;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "close error: " + e.getMessage());
        }
    }

    @Override
    public void addTransferListener(androidx.media3.datasource.DataSource.TransferListener transferListener) {
        // not needed
    }
}
