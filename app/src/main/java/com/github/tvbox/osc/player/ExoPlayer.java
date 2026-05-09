package com.github.tvbox.osc.player;

import android.content.Context;
import android.util.Pair;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;

import com.github.tvbox.osc.util.AudioTrackMemory;
import com.github.tvbox.osc.util.LOG;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import androidx.annotation.NonNull;
import androidx.media3.common.text.Cue;

import xyz.doikki.videoplayer.exo.Media3ExoPlayer;

public class ExoPlayer extends Media3ExoPlayer {

    private static AudioTrackMemory memory;

    public ExoPlayer(Context context) {
        super(context);
        memory = AudioTrackMemory.getInstance(context);
    }

    /**
     * 获取所有轨道信息（音轨/字幕）
     */
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        try {
            MappingTrackSelector.MappedTrackInfo mappedInfo = mTrackSelector.getCurrentMappedTrackInfo();
            if (mappedInfo == null) return data;

            DefaultTrackSelector.Parameters params = mTrackSelector.getParameters();

            for (int rendererIndex = 0; rendererIndex < mappedInfo.getRendererCount(); rendererIndex++) {
                int type = mappedInfo.getRendererType(rendererIndex);
                if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) continue;

                androidx.media3.exoplayer.source.TrackGroupArray groups = mappedInfo.getTrackGroups(rendererIndex);
                DefaultTrackSelector.SelectionOverride override = params.getSelectionOverride(rendererIndex, groups);
                boolean hasSelected = false;

                for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
                    TrackGroup group = groups.get(groupIndex);
                    for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                        Format fmt = group.getFormat(trackIndex);
                        TrackInfoBean bean = new TrackInfoBean();
                        bean.language = getLanguage(fmt);
                        bean.name = getName(fmt);
                        bean.groupIndex = groupIndex;
                        bean.index = trackIndex;

                        boolean selected = false;
                        if (override != null) {
                            if (override.groupIndex == groupIndex) {
                                for (int t : override.tracks) {
                                    if (t == trackIndex) {
                                        selected = true;
                                        hasSelected = true;
                                        break;
                                    }
                                }
                            }
                        } else if (type == C.TRACK_TYPE_AUDIO && !hasSelected) {
                            selected = true;
                            hasSelected = true;
                        }
                        bean.selected = selected;

                        if (type == C.TRACK_TYPE_AUDIO) {
                            data.addAudio(bean);
                        } else {
                            // 字幕轨道: 添加格式信息到名称
                            String formatInfo = "";
                            if (fmt.sampleMimeType != null) {
                                formatInfo = formatSubtitleMime(fmt.sampleMimeType);
                            }
                            if (!formatInfo.isEmpty()) {
                                bean.name = (bean.language.isEmpty() ? "未知" : bean.language) + " (" + formatInfo + ")";
                            } else {
                                bean.name = bean.language.isEmpty() ? "字幕" : bean.language;
                            }
                            data.addSubtitle(bean);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.i("getTrackInfo error: " + e.getMessage());
        }
        return data;
    }

    /**
     * 设置当前播放的音轨
     */
    public void setTrack(int groupIndex, int trackIndex, String playKey) {
        try {
            MappingTrackSelector.MappedTrackInfo mappedInfo = mTrackSelector.getCurrentMappedTrackInfo();
            if (mappedInfo == null) {
                LOG.i("setTrack: MappedTrackInfo is null");
                return;
            }

            int audioRendererIndex = findAudioRendererIndex(mappedInfo);
            if (audioRendererIndex == C.INDEX_UNSET) {
                LOG.i("setTrack: No audio renderer found");
                return;
            }

            androidx.media3.exoplayer.source.TrackGroupArray audioGroups = mappedInfo.getTrackGroups(audioRendererIndex);
            if (!isTrackIndexValid(audioGroups, groupIndex, trackIndex)) {
                LOG.i("setTrack: Invalid track index - group:" + groupIndex + ", track:" + trackIndex);
                return;
            }

            DefaultTrackSelector.SelectionOverride newOverride = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
            DefaultTrackSelector.Parameters.Builder builder = mTrackSelector.getParameters().buildUpon();
            builder.clearSelectionOverrides(audioRendererIndex);
            builder.setSelectionOverride(audioRendererIndex, audioGroups, newOverride);
            mTrackSelector.setParameters(builder.build());

            if (!playKey.isEmpty()) {
                memory.save(playKey, groupIndex, trackIndex);
            }
        } catch (Exception e) {
            LOG.i("setTrack error: " + e.getMessage());
        }
    }

    /**
     * 加载上一次选中的音轨
     */
    public void loadDefaultTrack(String playKey) {
        Pair<Integer, Integer> pair = memory.exoLoad(playKey);
        if (pair == null) return;

        MappingTrackSelector.MappedTrackInfo mappedInfo = mTrackSelector.getCurrentMappedTrackInfo();
        if (mappedInfo == null) return;

        int audioRendererIndex = findAudioRendererIndex(mappedInfo);
        if (audioRendererIndex == C.INDEX_UNSET) return;

        androidx.media3.exoplayer.source.TrackGroupArray audioGroups = mappedInfo.getTrackGroups(audioRendererIndex);
        int groupIndex = pair.first;
        int trackIndex = pair.second;
        if (!isTrackIndexValid(audioGroups, groupIndex, trackIndex)) return;

        DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
        DefaultTrackSelector.Parameters.Builder builder = mTrackSelector.getParameters().buildUpon();
        builder.clearSelectionOverrides(audioRendererIndex);
        builder.setSelectionOverride(audioRendererIndex, audioGroups, override);
        mTrackSelector.setParameters(builder.build());
    }

    private int findAudioRendererIndex(MappingTrackSelector.MappedTrackInfo mappedInfo) {
        for (int i = 0; i < mappedInfo.getRendererCount(); i++) {
            if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    private boolean isTrackIndexValid(androidx.media3.exoplayer.source.TrackGroupArray groups, int groupIndex, int trackIndex) {
        if (groupIndex < 0 || groupIndex >= groups.length) return false;
        TrackGroup group = groups.get(groupIndex);
        return trackIndex >= 0 && trackIndex < group.length;
    }

    // ============ 语言/轨道名称工具 ============

    private static final Map<String, String> LANG_MAP = new HashMap<>();

    static {
        LANG_MAP.put("zh", "中文");
        LANG_MAP.put("zh-cn", "中文");
        LANG_MAP.put("en", "英语");
        LANG_MAP.put("en-us", "英语");
    }

    private String getLanguage(Format fmt) {
        String lang = fmt.language;
        if (lang == null || lang.isEmpty() || "und".equalsIgnoreCase(lang)) {
            return "未知";
        }
        String name = LANG_MAP.get(lang.toLowerCase());
        return name != null ? name : lang;
    }

    private String getName(Format fmt) {
        String channelLabel;
        if (fmt.channelCount <= 0) {
            channelLabel = "";
        } else if (fmt.channelCount == 1) {
            channelLabel = "单声道";
        } else if (fmt.channelCount == 2) {
            channelLabel = "立体声";
        } else {
            channelLabel = fmt.channelCount + " 声道";
        }
        String codec = "";
        if (fmt.sampleMimeType != null) {
            String mime = fmt.sampleMimeType.substring(fmt.sampleMimeType.indexOf('/') + 1);
            codec = mime.toUpperCase();
        }
        return String.join(", ", channelLabel, codec);
    }
    /**
     * 选择字幕轨道（通过Media3的TrackSelector）
     */
    public void selectSubtitleTrack(int groupIndex, int trackIndex) {
        try {
            MappingTrackSelector.MappedTrackInfo mappedInfo = mTrackSelector.getCurrentMappedTrackInfo();
            if (mappedInfo == null) return;

            int textRendererIndex = -1;
            for (int i = 0; i < mappedInfo.getRendererCount(); i++) {
                if (mappedInfo.getRendererType(i) == C.TRACK_TYPE_TEXT) {
                    textRendererIndex = i;
                    break;
                }
            }
            if (textRendererIndex < 0) return;

            androidx.media3.exoplayer.source.TrackGroupArray textGroups = mappedInfo.getTrackGroups(textRendererIndex);
            if (groupIndex < 0 || groupIndex >= textGroups.length) return;
            if (trackIndex < 0 || trackIndex >= textGroups.get(groupIndex).length) return;

            DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex);
            DefaultTrackSelector.Parameters.Builder builder = mTrackSelector.getParameters().buildUpon();
            builder.clearSelectionOverrides(textRendererIndex);
            builder.setSelectionOverride(textRendererIndex, textGroups, override);
            mTrackSelector.setParameters(builder.build());
        } catch (Exception e) {
            LOG.i("selectSubtitleTrack error: " + e.getMessage());
        }
    }

    /**
     * 初始化Exo字幕监听(onCues)
     */
    public void initSubtitleCueListener() {
        if (mExoPlayer == null) return;
        try {
            mExoPlayer.addListener(new androidx.media3.common.Player.Listener() {
                @Override
                public void onCues(@NonNull java.util.List<Cue> cues) {
                    if (cues == null || cues.isEmpty()) return;
                    StringBuilder sb = new StringBuilder();
                    for (Cue cue : cues) {
                        if (cue.text != null && cue.text.length() > 0) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(cue.text);
                        }
                    }
                    final String text = sb.toString();
                    if (!text.isEmpty() && mExoSubtitleListener != null) {
                        mExoSubtitleListener.onSubtitleText(text);
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e("ExoPlayer", "initSubtitleCueListener failed", e);
        }
    }
}