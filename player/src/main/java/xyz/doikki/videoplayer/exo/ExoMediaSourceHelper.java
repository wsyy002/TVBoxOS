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
 * MediaSource 辅助类 — Media3 版
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

    public MediaSource getMediaSource(String uri, Map<String, String> headers) {
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(uri));

        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                mContext,
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
        );

        return new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
    }
}
