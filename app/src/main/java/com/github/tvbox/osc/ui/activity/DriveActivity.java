package com.github.tvbox.osc.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.DriveFolderFile;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.DriveAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.StorageDriveType;
import com.github.tvbox.osc.viewmodel.drive.AbstractDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.FTPDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.LocalDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.SMBDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.WebDAVDriveViewModel;
import com.google.gson.JsonObject;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 文件管理器（本地/WebDAV/SMB/FTP）
 */
public class DriveActivity extends BaseActivity {

    private TextView txtTitle;
    private TvRecyclerView mGridView;
    private ImageButton btnAddServer;
    private ImageButton btnRemoveServer;
    private ImageButton btnSort;
    private DriveAdapter adapter = new DriveAdapter();
    private List<DriveFolderFile> drives = new ArrayList<>();
    private AbstractDriveViewModel viewModel = null;
    private boolean delMode = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_drive;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        initData();
    }

    private void initView() {
        txtTitle = findViewById(R.id.textView);
        mGridView = findViewById(R.id.mGridView);
        btnAddServer = findViewById(R.id.btnAddServer);
        btnRemoveServer = findViewById(R.id.btnRemoveServer);
        btnSort = findViewById(R.id.btnSort);

        btnAddServer.setVisibility(View.VISIBLE);
        btnRemoveServer.setVisibility(View.VISIBLE);

        btnRemoveServer.setOnClickListener(v -> {
            toggleDelMode();
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            onBackPressed();
        });

        btnAddServer.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showAddDriveDialog();
        });

        mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, V7LinearLayoutManager.VERTICAL, false));
        mGridView.setSpacingWithMargins(AutoSizeUtils.mm2px(mContext, 10), 0);
        mGridView.setAdapter(adapter);

        int sortType = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        adapter.bindToRecyclerView(mGridView);

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0 && position < adapter.getData().size())
                    adapter.getData().get(position).isSelected = false;
            }

            @Override
            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0 && position < adapter.getData().size())
                    adapter.getData().get(position).isSelected = true;
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position >= adapter.getData().size()) return;
                DriveFolderFile selectedItem = adapter.getItem(position);

                if (delMode) {
                    if (selectedItem.getDriveData() != null) {
                        RoomDataManger.deleteDrive(selectedItem.getDriveData().id);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                    }
                    return;
                }

                // 返回按钮
                if (selectedItem.parentFolder != null && selectedItem.name == null) {
                    goBack();
                    return;
                }

                if (viewModel == null) {
                    initViewModel(selectedItem);
                    if (!selectedItem.isFile) {
                        loadDriveData();
                        return;
                    }
                }

                if (!selectedItem.isFile) {
                    viewModel.setCurrentDriveNote(selectedItem);
                    loadDriveData();
                } else {
                    // 播放视频文件
                    playFile(selectedItem);
                }
            }
        });
    }

    private void initViewModel(DriveFolderFile item) {
        switch (item.getDriveType()) {
            case LOCAL:
                viewModel = new LocalDriveViewModel();
                break;
            case WEBDAV:
                viewModel = new WebDAVDriveViewModel();
                break;
            case SMB:
                viewModel = new SMBDriveViewModel();
                break;
            case FTP:
                viewModel = new FTPDriveViewModel();
                break;
        }
        if (viewModel != null) {
            viewModel.setCurrentDrive(item);
        }
    }

    private void loadDriveData() {
        if (viewModel == null) return;
        showLoading();
        viewModel.loadData(new AbstractDriveViewModel.LoadDataCallback() {
            @Override
            public void callback(List<DriveFolderFile> items, boolean fromCache) {
                runOnUiThread(() -> {
                    adapter.setNewData(items);
                    showSuccess();
                });
            }

            @Override
            public void fail(String msg) {
                runOnUiThread(() -> {
                    Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
                    showSuccess();
                });
            }
        });
    }

    private void goBack() {
        DriveFolderFile parent = viewModel != null ? viewModel.getCurrentDriveNote() : null;
        if (parent != null && parent.parentFolder != null) {
            DriveFolderFile grandParent = parent.parentFolder;
            // 回到根目录
            if (grandParent.parentFolder == grandParent || grandParent.name == null) {
                returnToRoot();
            } else {
                viewModel.setCurrentDriveNote(grandParent);
                loadDriveData();
            }
        } else {
            returnToRoot();
        }
    }

    private void returnToRoot() {
        viewModel = null;
        btnAddServer.setVisibility(View.VISIBLE);
        btnRemoveServer.setVisibility(View.VISIBLE);
        txtTitle.setText("文件管理器");
        initData();
    }

    private void toggleDelMode() {
        delMode = !delMode;
        btnRemoveServer.setColorFilter(delMode ? 0xFFFF9800 : 0xFFFFFFFF);
        adapter.toggleDelMode(delMode);
    }

    private void showAddDriveDialog() {
        StorageDriveType.TYPE[] types = StorageDriveType.TYPE.values();
        SelectDialog<StorageDriveType.TYPE> dialog = new SelectDialog<>(this);
        dialog.setTip("选择存储类型");
        String[] typeNames = StorageDriveType.getTypeNames();
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<StorageDriveType.TYPE>() {
            @Override
            public void click(StorageDriveType.TYPE value, int pos) {
                dialog.dismiss();
                if (value == StorageDriveType.TYPE.LOCAL) {
                    addLocalDrive();
                } else if (value == StorageDriveType.TYPE.WEBDAV) {
                    showWebdavDialog(null);
                } else if (value == StorageDriveType.TYPE.SMB) {
                    showSmbDialog(null);
                } else if (value == StorageDriveType.TYPE.FTP) {
                    showFtpDialog(null);
                }
            }

            @Override
            public String getDisplay(StorageDriveType.TYPE val) {
                return typeNames[val.ordinal()];
            }
        }, new DiffUtil.ItemCallback<StorageDriveType.TYPE>() {
            @Override
            public boolean areItemsTheSame(@NonNull StorageDriveType.TYPE oldItem, @NonNull StorageDriveType.TYPE newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull StorageDriveType.TYPE oldItem, @NonNull StorageDriveType.TYPE newItem) {
                return oldItem.equals(newItem);
            }
        }, Arrays.asList(types), 0);
        dialog.show();
    }

    private void addLocalDrive() {
        // 简单的本地目录选择对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("输入本地目录路径");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText("/storage/emulated/0/");
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String path = input.getText().toString().trim();
            if (!path.isEmpty()) {
                RoomDataManger.insertDriveRecord(path, StorageDriveType.TYPE.LOCAL.ordinal(), null);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showWebdavDialog(StorageDrive drive) {
        showConfigDialog("WebDAV", drive, new String[]{"服务器地址", "用户名", "密码"},
                new String[]{"https://example.com/webdav", "", ""},
                StorageDriveType.TYPE.WEBDAV);
    }

    private void showSmbDialog(StorageDrive drive) {
        showConfigDialog("SMB 共享", drive, new String[]{"服务器地址", "用户名", "密码", "域"},
                new String[]{"smb://192.168.1.100/share", "", "", ""},
                StorageDriveType.TYPE.SMB);
    }

    private void showFtpDialog(StorageDrive drive) {
        showConfigDialog("FTP 服务器", drive, new String[]{"主机地址", "端口", "用户名", "密码"},
                new String[]{"192.168.1.100", "21", "", ""},
                StorageDriveType.TYPE.FTP);
    }

    private void showConfigDialog(String title, StorageDrive existingDrive,
                                   String[] labels, String[] defaults,
                                   StorageDriveType.TYPE type) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(title);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_api, null);
        builder.setView(dialogView);

        // 简单处理：使用 dialog_api 布局，只有一个输入框
        EditText input = dialogView.findViewById(android.R.id.edit);
        if (input == null) {
            input = new EditText(this);
            dialogView = input;
            builder.setView(input);
        }

        builder.setPositiveButton("确定", (dialog, which) -> {
            String text = input.getText().toString().trim();
            final String finalText = text;
            if (finalText.isEmpty()) {
                Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show();
                return;
            }
            JsonObject config = new JsonObject();
            config.addProperty("url", finalText);

            String name = title + " - " + finalText.substring(0, Math.min(finalText.length(), 30));
            RoomDataManger.insertDriveRecord(name, type.ordinal(), config.toString());
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void playFile(DriveFolderFile file) {
        // 通过本地代理或直接 URL 播放
        String playUrl;
        if (file.getDriveType() == StorageDriveType.TYPE.LOCAL) {
            // 本地文件
            playUrl = viewModel.getCurrentDrive().name
                    + viewModel.getCurrentDriveNote().getAccessingPathStr()
                    + viewModel.getCurrentDriveNote().name + "/" + file.name;
        } else if (file.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            // WebDAV 文件 - 通过 sardine 获取直接 URL
            playUrl = viewModel.getCurrentDrive().getDriveData().configJson + "/"
                    + viewModel.getCurrentDriveNote().getAccessingPathStr()
                    + viewModel.getCurrentDriveNote().name + "/" + file.name;
            // 通过本地代理
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(playUrl);
        } else if (file.getDriveType() == StorageDriveType.TYPE.SMB) {
            // SMB - 通过本地代理
            String smbUrl = viewModel.getCurrentDrive().name
                    + viewModel.getCurrentDriveNote().getAccessingPathStr()
                    + viewModel.getCurrentDriveNote().name + "/" + file.name;
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(smbUrl);
        } else if (file.getDriveType() == StorageDriveType.TYPE.FTP) {
            // FTP - 通过本地代理
            String ftpUrl = "ftp://" + viewModel.getCurrentDrive().getDriveData().name + "/"
                    + viewModel.getCurrentDriveNote().getAccessingPathStr()
                    + viewModel.getCurrentDriveNote().name + "/" + file.name;
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(ftpUrl);
        } else {
            playUrl = file.name;
        }

        // 构造 VodInfo 供 PlayActivity 播放
        com.github.tvbox.osc.bean.VodInfo vodInfo = new com.github.tvbox.osc.bean.VodInfo();
        vodInfo.id = "drive_" + System.currentTimeMillis();
        vodInfo.name = file.name;
        vodInfo.sourceKey = "drive";

        // ���建系列和剧集结构
        java.util.LinkedHashMap<String, java.util.List<com.github.tvbox.osc.bean.VodInfo.VodSeries>> seriesMap = new java.util.LinkedHashMap<>();
        java.util.List<com.github.tvbox.osc.bean.VodInfo.VodSeries> seriesList = new java.util.ArrayList<>();
        seriesList.add(new com.github.tvbox.osc.bean.VodInfo.VodSeries(file.name, playUrl));
        seriesMap.put("drive", seriesList);
        vodInfo.seriesMap = seriesMap;
        vodInfo.playFlag = "drive";
        vodInfo.playIndex = 0;

        java.util.ArrayList<com.github.tvbox.osc.bean.VodInfo.VodSeriesFlag> flags = new java.util.ArrayList<>();
        flags.add(new com.github.tvbox.osc.bean.VodInfo.VodSeriesFlag("drive"));
        vodInfo.seriesFlags = flags;

        com.github.tvbox.osc.base.App.getInstance().setVodInfo(vodInfo);

        android.content.Intent intent = new Intent(this, PlayActivity.class);
        startActivity(intent);
    }

    private void initData() {
        txtTitle.setText("文件管理器");
        if (drives.isEmpty()) {
            drives = new ArrayList<>();
            List<StorageDrive> storageDrives = RoomDataManger.getAllDrives();
            for (StorageDrive sd : storageDrives) {
                DriveFolderFile drive = new DriveFolderFile(sd);
                if (delMode) drive.isDelMode = true;
                drives.add(drive);
            }
        }
        adapter.setNewData(drives);
        btnAddServer.setVisibility(View.VISIBLE);
        btnRemoveServer.setVisibility(View.VISIBLE);
        showSuccess();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_DRIVE_REFRESH) {
            drives.clear();
            initData();
        }
    }

    @Override
    public void onBackPressed() {
        if (viewModel != null) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
