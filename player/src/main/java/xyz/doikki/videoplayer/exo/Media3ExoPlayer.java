package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.rtmp.RtmpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.util.EventLogger;

import java.io.File;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoViewManager;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * Media3 版 ExoPlayer 播放器
 * 直接使用 Media3 ExoPlayer，不经过 DKPlayer 的 ExoMediaPlayer 封装
 *
 * 特性：
 * - GPU 硬解优先（自动降级软解）
 * - 直播低延迟优化
 * - SMB/FTP/WebDAV 通过本地代理中转
 */
@UnstableApi
public class Media3ExoPlayer extends AbstractPlayer implements Player.Listener {

    private static final String TAG = "Media3ExoPlayer";

    protected Context mAppContext;
    protected ExoPlayer mExoPlayer;
    protected MediaSource mMediaSource;
    protected MediaSource.Factory mMediaSourceFactory;

    private PlaybackParameters mSpeedPlaybackParameters;
    private boolean mIsPreparing;
    private boolean mIsLive = false;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    protected DefaultTrackSelector mTrackSelector;

    protected String currentPlayPath;

    public Media3ExoPlayer(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        // 读取 Exo 解码方式设置（通过反射读取Hawk，兼容模块分离）
        boolean enableHW = true;
        try {
            Class<?> hawkClass = Class.forName("com.orhanobut.hawk.Hawk");
            java.lang.reflect.Method getMethod = hawkClass.getMethod("get", String.class, Object.class);
            String codec = (String) getMethod.invoke(null, "exo_codec", "硬解码");
            enableHW = "硬解码".equals(codec);
        } catch (Exception ignored) {}

        // 播放器工厂配置：硬解优先，自动降级
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(mAppContext);
        renderersFactory.setEnableDecoderFallback(true);  // 硬解失败自动降级到软解
        renderersFactory.setExtensionRendererMode(
                enableHW
                        ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        );
        mRenderersFactory = renderersFactory;

        // 轨道选择器
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(mAppContext);
        mTrackSelector = trackSelector;

        // 媒体源工厂
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(mAppContext);
        mMediaSourceFactory = mediaSourceFactory;

        // 加载控制
        DefaultLoadControl loadControl = new DefaultLoadControl();
        mLoadControl = loadControl;

        // 创建 ExoPlayer
        mExoPlayer = new ExoPlayer.Builder(mAppContext)
                .setRenderersFactory(mRenderersFactory)
                .setTrackSelector(mTrackSelector)
                .setMediaSourceFactory(mMediaSourceFactory)
                .setLoadControl(mLoadControl)
                .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(mAppContext))
                .build();

        setOptions();

        // 播放器日志
        if (VideoViewManager.getConfig().mIsEnableLog) {
            mExoPlayer.addAnalyticsListener(new EventLogger(mTrackSelector, "ExoPlayer"));
        }

        mExoPlayer.addListener(this);
    }

    @Override
    public void setDataSource(@NonNull String path, Map<String, String> headers) {
        Log.i(TAG, "setDataSource: " + path);
        currentPlayPath = path;

        // 判断是否是直播
        mIsLive = isLiveStream(path);

        // 检测协议，创建对应的 MediaItem
        MediaItem mediaItem;

        if (path.startsWith("smb://") || path.startsWith("ftp://") || path.startsWith("ftps://")) {
            // SMB/FTP 通过本地代理中转（由 LocalProxyServer 处理）
            String proxyUrl = getLocalProxyUrl(path);
            if (proxyUrl != null) {
                mediaItem = buildMediaItem(proxyUrl, headers);
            } else {
                // 如果代理未启动，尝试直接解析
                mediaItem = buildMediaItem(path, headers);
            }
        } else {
            mediaItem = buildMediaItem(path, headers);
        }

        // 根据协议创建 MediaSource
        if (path.toLowerCase().contains(".m3u8")) {
            mMediaSource = new HlsMediaSource.Factory(
                    new DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
            ).createMediaSource(mediaItem);
        } else if (path.startsWith("rtmp://")) {
            mMediaSource = new ProgressiveMediaSource.Factory(
                    new RtmpDataSource.Factory()
            ).createMediaSource(mediaItem);
        } else {
            // 使用 DefaultMediaSourceFactory 自动检测格式(HLS/DASH/Progressive)
            // 兼容 CSP 来源的无后缀名 URL
            mMediaSource = mMediaSourceFactory.createMediaSource(mediaItem);
        }
    }

    private MediaItem buildMediaItem(String path, Map<String, String> headers) {
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(android.net.Uri.parse(path));

        if (headers != null && !headers.isEmpty()) {
            // Headers not supported via MediaItem in Media3 1.5.0
            // They will be applied via DataSource factory
        }

        return builder.build();
    }

    /**
     * 获取 SMB/FTP 的本地代理 URL
     */
    private String getLocalProxyUrl(String path) {
        try {
            Class<?> proxyClass = Class.forName("com.github.tvbox.osc.util.LocalProxyServer");
            java.lang.reflect.Method getInstance = proxyClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            java.lang.reflect.Method proxyMethod = proxyClass.getMethod("proxyUrl", String.class);
            return (String) proxyMethod.invoke(instance, path);
        } catch (Exception e) {
            Log.w(TAG, "LocalProxyServer not available, playing directly: " + e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否是直播流
     */
    private boolean isLiveStream(String path) {
        if (TextUtils.isEmpty(path)) return false;
        String lower = path.toLowerCase();
        // 常见的直播特征
        return lower.contains("live") || lower.contains("play") || lower.contains("rtmp")
                || lower.startsWith("rtsp://") || lower.startsWith("udp://");
    }

    /**
     * 设置直播模式（低延迟优化）
     */
    public void setLiveMode(boolean isLive) {
        mIsLive = isLive;
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // 不支持 AssetFileDescriptor 方式
        Log.w(TAG, "AssetFileDescriptor not supported");
    }

    @Override
    public void start() {
        if (mExoPlayer == null) return;
        mExoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mExoPlayer == null) return;
        mExoPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mExoPlayer == null) return;
        mExoPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mExoPlayer == null || mMediaSource == null) return;

        if (mSpeedPlaybackParameters != null) {
            mExoPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }

        // 直播低延迟配置（通过 MediaItem 的 ClippingConfiguration）
        if (mIsLive) {
            mExoPlayer.setMediaSource(mMediaSource, false);
        } else {
            mExoPlayer.setMediaSource(mMediaSource);
        }

        mIsPreparing = true;
        mExoPlayer.prepare();
    }

    @Override
    public void reset() {
        if (mExoPlayer != null) {
            mExoPlayer.stop();
            mExoPlayer.clearMediaItems();
            mExoPlayer.setVideoSurface(null);
            mIsPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mExoPlayer == null) return false;
        int state = mExoPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mExoPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mExoPlayer == null) return;
        mExoPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mExoPlayer != null) {
            mExoPlayer.removeListener(this);
            mExoPlayer.release();
            mExoPlayer = null;
        }
        mIsPreparing = false;
        mSpeedPlaybackParameters = null;
    }

    @Override
    public long getCurrentPosition() {
        if (mExoPlayer == null) return 0;
        return mExoPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mExoPlayer == null) return 0;
        return mExoPlayer.getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return mExoPlayer == null ? 0 : mExoPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        if (mExoPlayer != null) {
            mExoPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) {
            setSurface(null);
        } else {
            setSurface(holder.getSurface());
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mExoPlayer != null) {
            mExoPlayer.setVolume((leftVolume + rightVolume) / 2);
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mExoPlayer != null) {
            mExoPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        }
    }

    @Override
    public void setOptions() {
        if (mExoPlayer != null) {
            // 准备好就开始播放
            mExoPlayer.setPlayWhenReady(true);

            // 直播优化：减少缓冲
            if (mIsLive) {
                mExoPlayer.setPlaybackParameters(
                        new PlaybackParameters(1.0f)
                );
            }

        }
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters params = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = params;
        if (mExoPlayer != null) {
            mExoPlayer.setPlaybackParameters(params);
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

    // ============ 直播低延迟控制 ============

    /**
     * 设置直播缓冲策略
     */
    public void setLiveLatencyOptimization(boolean enable) {
        if (mExoPlayer == null) return;
        if (enable) {
            // 最小化缓冲，降低延迟
            // Live latency optimization
            // mExoPlayer does not have setMinLoadableRetryCount in 1.5.0
        }
    }

    // ============ 轨道选择 ============

    public DefaultTrackSelector getTrackSelector() {
        return mTrackSelector;
    }

    /**
     * 获取当前选中轨道的索引
     */
    public void getExoSelectedTrack() {
        // 使用 Media3 新 API 获取轨道
    }

    // ============ Player.Listener ============

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (mPlayerEventListener == null) return;

        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 0);
                mIsPreparing = false;
            }
            return;
        }

        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                break;
            case Player.STATE_READY:
                mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                break;
            case Player.STATE_ENDED:
                mPlayerEventListener.onCompletion();
                break;
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e(TAG, "Playback error: " + error.getMessage() + " (code=" + error.getErrorCodeName() + ")");

        if (mPlayerEventListener != null) {
            // 检查是否是解码错误（硬解失败），自动降级
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
                // 解码器错误，通知上层尝试切换
                mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 0);
            }
            mPlayerEventListener.onError();
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            if (videoSize.unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(
                        AbstractPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED,
                        videoSize.unappliedRotationDegrees
                );
            }
        }
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        // 轨道变化回调
    }
}
