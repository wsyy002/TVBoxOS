package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.Map;

/**
 * MediaSource 辅助类
 * 适配 Media3 版本
 */
public class ExoMediaSourceHelper {

    private static ExoMediaSourceHelper sInstance;
    private Context mContext;

    private ExoMediaSourceHelper(Context context) {
        mContext = context.getApplicationContext();
    }

    public static ExoMediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExoMediaSourceHelper(context);
        }
        return sInstance;
    }

    public MediaSource getMediaSource(String url, Map<String, String> headers) {
        MediaItem mediaItem;

        if (headers != null && !headers.isEmpty()) {
            MediaItem.RequestHeaders.Builder headerBuilder = new MediaItem.RequestHeaders.Builder();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerBuilder.add(entry.getKey(), entry.getValue());
            }
            mediaItem = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setRequestHeaders(headerBuilder.build())
                    .build();
        } else {
            mediaItem = MediaItem.fromUri(Uri.parse(url));
        }

        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                mContext,
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
        );

        return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
    }
}
