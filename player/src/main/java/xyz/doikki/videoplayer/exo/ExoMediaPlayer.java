package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.util.EventLogger;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoViewManager;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * DKPlayer 兼容层 — 使用 Media3 ExoPlayer
 * 注：推荐使用 Media3ExoPlayer，此类仅用于兼容旧版 DKPlayer 调用
 */
@UnstableApi
public class ExoMediaPlayer extends AbstractPlayer implements Player.Listener {

    protected Context mAppContext;
    protected ExoPlayer mInternalPlayer;
    protected MediaSource mMediaSource;

    private PlaybackParameters mSpeedPlaybackParameters;
    private boolean mIsPreparing;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    protected DefaultTrackSelector mTrackSelector;

    protected DefaultTrackSelector trackSelector;

    protected String currentPlayPath;

    public ExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(mAppContext);
        renderersFactory.setEnableDecoderFallback(true);
        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        mRenderersFactory = renderersFactory;

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(mAppContext);
        mTrackSelector = trackSelector;
        this.trackSelector = trackSelector;

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(mAppContext);
        DefaultLoadControl loadControl = new DefaultLoadControl();

        mInternalPlayer = new ExoPlayer.Builder(mAppContext)
                .setRenderersFactory(mRenderersFactory)
                .setTrackSelector(mTrackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(mAppContext))
                .build();

        setOptions();

        if (VideoViewManager.getConfig().mIsEnableLog) {
            mInternalPlayer.addAnalyticsListener(new EventLogger(mTrackSelector, "ExoPlayer"));
        }

        mInternalPlayer.addListener(this);
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        if (trackSelector instanceof DefaultTrackSelector) {
            mTrackSelector = (DefaultTrackSelector) trackSelector;
        }
    }

    public void setRenderersFactory(RenderersFactory renderersFactory) {
        mRenderersFactory = renderersFactory;
    }

    public void setLoadControl(LoadControl loadControl) {
        mLoadControl = loadControl;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        Log.i("Tvbox-runtime", "echo-setDataSource:" + path);
        currentPlayPath = path;

        // 创建 MediaItem
        androidx.media3.common.MediaItem mediaItem = 
            new androidx.media3.common.MediaItem.Builder()
                .setUri(android.net.Uri.parse(path))
                .build();

        // 根据协议选择 MediaSource
        if (path.toLowerCase().contains(".m3u8")) {
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(mAppContext);
            mMediaSource = new androidx.media3.exoplayer.hls.HlsMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
        } else {
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(mAppContext);
            mMediaSource = new androidx.media3.exoplayer.source.ProgressiveMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
        }
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // 不支持AssetFileDescriptor方式
    }

    @Override
    public void start() {
        if (mInternalPlayer == null) return;
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mInternalPlayer == null) return;
        mInternalPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mInternalPlayer == null) return;
        mInternalPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mInternalPlayer == null || mMediaSource == null) return;
        if (mSpeedPlaybackParameters != null) {
            mInternalPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        mIsPreparing = true;
        mInternalPlayer.setMediaSource(mMediaSource);
        mInternalPlayer.prepare();
    }

    @Override
    public void reset() {
        if (mInternalPlayer != null) {
            mInternalPlayer.stop();
            mInternalPlayer.clearMediaItems();
            mInternalPlayer.setVideoSurface(null);
            mIsPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null) return false;
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mInternalPlayer == null) return;
        mInternalPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mInternalPlayer != null) {
            mInternalPlayer.removeListener(this);
            mInternalPlayer.release();
            mInternalPlayer = null;
        }
        mIsPreparing = false;
        mSpeedPlaybackParameters = null;
    }

    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null) return 0;
        return mInternalPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mInternalPlayer == null) return 0;
        return mInternalPlayer.getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return mInternalPlayer == null ? 0 : mInternalPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        if (mInternalPlayer != null) {
            mInternalPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) setSurface(null);
        else setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mInternalPlayer != null)
            mInternalPlayer.setVolume((leftVolume + rightVolume) / 2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mInternalPlayer != null)
            mInternalPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters params = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = params;
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlaybackParameters(params);
        }
    }

    @Override
    public float getSpeed() {
        if (mSpeedPlaybackParameters != null) {
            return mSpeedPlaybackParameters.speed;
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        return PlayerUtils.getNetSpeed(mAppContext);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (mPlayerEventListener == null) return;
        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                mIsPreparing = false;
            }
            return;
        }
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                break;
            case Player.STATE_READY:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                break;
            case Player.STATE_ENDED:
                mPlayerEventListener.onCompletion();
                break;
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onError();
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            if (videoSize.unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, videoSize.unappliedRotationDegrees);
            }
        }
    }
}
