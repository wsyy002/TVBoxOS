package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DriveFolderFile;
import com.github.tvbox.osc.util.StorageDriveType;

/**
 * 驱动文件列表适配器
 */
public class DriveAdapter extends BaseQuickAdapter<DriveFolderFile, BaseViewHolder> {

    private boolean delMode = false;
    private OnItemClickListener mOnItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(DriveFolderFile item, int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public DriveAdapter() {
        super(R.layout.item_drive_file);
    }

    public void toggleDelMode(boolean delMode) {
        this.delMode = delMode;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(@NonNull BaseViewHolder helper, DriveFolderFile item) {
        ImageView ivIcon = helper.getView(R.id.ivIcon);
        TextView tvName = helper.getView(R.id.tvName);
        TextView tvInfo = helper.getView(R.id.tvInfo);
        FrameLayout delFrame = helper.getView(R.id.delFrameLayout);

        // 给每个itemView设置点击监听（TvRecyclerView OnItemListener可能在某些设备不触发）
        final int pos = helper.getAdapterPosition();
        helper.itemView.setOnClickListener(v -> {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(item, pos);
            }
        });

        if (item.parentFolder == item || (item.name == null && item.parentFolder == null)) {
            ivIcon.setImageResource(R.drawable.ic_folder_back);
            tvName.setText("返回上一级");
            tvInfo.setVisibility(View.GONE);
            delFrame.setVisibility(View.GONE);
            return;
        }

        // 删除模式
        delFrame.setVisibility(delMode ? View.VISIBLE : View.GONE);

        // 图标
        if (item.isFile) {
            if (StorageDriveType.isVideoType(item.fileType)) {
                ivIcon.setImageResource(R.drawable.icon_video);
            } else {
                ivIcon.setImageResource(R.drawable.icon_file);
            }
            tvInfo.setText(formatSize(item.size));
            tvInfo.setVisibility(View.VISIBLE);
        } else {
            ivIcon.setImageResource(R.drawable.icon_folder);
            tvInfo.setVisibility(View.GONE);
        }

        tvName.setText(item.name != null ? item.name : "");
    }

    private String formatSize(long size) {
        if (size <= 0) return "";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double s = size;
        while (s >= 1024 && unitIndex < units.length - 1) {
            s /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", s, units[unitIndex]);
    }
}
