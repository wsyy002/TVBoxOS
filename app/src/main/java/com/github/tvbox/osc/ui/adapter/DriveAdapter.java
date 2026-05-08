package com.github.tvbox.osc.ui.adapter;

import android.view.View;
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
        View delIcon = helper.getView(R.id.delIcon);

        // 返回按钮
        if (item.parentFolder == item || (item.name == null && item.parentFolder == null)) {
            ivIcon.setImageResource(R.drawable.ic_folder_back);
            tvName.setText("返回上一级");
            tvInfo.setText("");
            delIcon.setVisibility(View.GONE);
            return;
        }

        if (delMode) {
            delIcon.setVisibility(View.VISIBLE);
        } else {
            delIcon.setVisibility(View.GONE);
        }

        if (item.isFile) {
            if (StorageDriveType.isVideoType(item.fileType)) {
                ivIcon.setImageResource(R.drawable.icon_video);
            } else {
                ivIcon.setImageResource(R.drawable.icon_file);
            }
            tvInfo.setText(formatSize(item.size));
        } else {
            ivIcon.setImageResource(R.drawable.icon_folder);
            tvInfo.setText("");
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
