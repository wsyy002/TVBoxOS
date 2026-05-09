package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.github.tvbox.osc.ui.dialog.BaseDialog;
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

public class DriveActivity extends BaseActivity {

    private TextView tvTitle;
    private TvRecyclerView mGridView;
    private TextView btnAddServer;
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
        tvTitle = findViewById(R.id.tvTitle);
        mGridView = findViewById(R.id.mGridView);
        btnAddServer = findViewById(R.id.btnAddServer);
        TextView btnHome = findViewById(R.id.btnHome);

        btnHome.setOnClickListener(v -> onBackPressed());

        btnAddServer.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showAddDriveDialog();
        });

        mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, V7LinearLayoutManager.VERTICAL, false));
        mGridView.setSpacingWithMargins(AutoSizeUtils.mm2px(mContext, 10), 0);
        mGridView.setAdapter(adapter);

        // 通过adapter设置item点击（比TvRecyclerView OnItemListener更可靠）
        adapter.setOnItemClickListener((item, position) -> onDriveItemClicked(item));

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
                onDriveItemClicked(adapter.getItem(position));
            }
        });

        // TvRecyclerView 不支持长按监听，删除通过按钮切换 delMode 实现
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
        tvTitle.setText("文件管理器");
        initData();
    }

    /**
     * 根级别存储操作菜单：浏览 / 编辑 / 删除
     */
    /**
     * 统一处理Drive列表item点击
     */
    private void onDriveItemClicked(DriveFolderFile selectedItem) {
        if (selectedItem == null) return;

        if (delMode) {
            if (selectedItem.getDriveData() != null) {
                RoomDataManger.deleteDrive(selectedItem.getDriveData().id);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                delMode = false;
                adapter.toggleDelMode(false);
            }
            return;
        }

        android.util.Log.i("DRIVE_CLICK", "Clicked: name=" + selectedItem.name
                + " driveData=" + (selectedItem.getDriveData() != null ? selectedItem.getDriveData().id : "null")
                + " parentFolder=" + (selectedItem.parentFolder != null ? "non-null" : "null")
                + " viewModel=" + (viewModel != null ? "exists" : "null"));

        if (selectedItem.parentFolder != null && selectedItem.name == null) {
            goBack();
            return;
        }

        if (selectedItem.getDriveData() != null && selectedItem.parentFolder == null) {
            // 根级别存储: 展示操作菜单（浏览/编辑/删除）
            showDriveActionMenu(selectedItem);
            return;
        }

        if (viewModel == null) {
            initViewModel(selectedItem);
            if (!selectedItem.isFile) {
                btnAddServer.setVisibility(View.GONE);
                loadDriveData();
                return;
            }
        }

        if (!selectedItem.isFile) {
            viewModel.setCurrentDriveNote(selectedItem);
            loadDriveData();
        } else {
            playFile(selectedItem);
        }
    }

    private void showDriveActionMenu(DriveFolderFile item) {
        String[] actions = new String[]{"浏览文件", "编辑配置", "删除"};
        SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip(item.name);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                if (pos == 0) {
                    // 浏览
                    btnAddServer.setVisibility(View.GONE);
                    tvTitle.setText(item.name);
                    initViewModel(item);
                    loadDriveData();
                } else if (pos == 1) {
                    // 编辑
                    showEditDialog(item);
                } else if (pos == 2) {
                    // 删除
                    showDeleteConfirm(item);
                }
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        }, Arrays.asList(actions), 0);
        dialog.show();
    }

    private void showDeleteConfirm(DriveFolderFile item) {
        BaseDialog dialog = new BaseDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        TextView tvMsg = view.findViewById(com.github.tvbox.osc.R.id.confirmation);
        if (tvMsg != null) {
            tvMsg.setText("确认删除存储 " + item.name + "？");
        }
        View btnOk = view.findViewById(com.github.tvbox.osc.R.id.btnConfirm);
        View btnCancel = view.findViewById(com.github.tvbox.osc.R.id.btnCancel);
        if (btnOk != null) {
            btnOk.setOnClickListener(v -> {
                if (item.getDriveData() != null) {
                    RoomDataManger.deleteDrive(item.getDriveData().id);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                }
                dialog.dismiss();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.setContentView(view);
        dialog.show();
    }

    private void showAddDriveDialog() {
        StorageDriveType.TYPE[] types = StorageDriveType.TYPE.values();
        SelectDialog<StorageDriveType.TYPE> dialog = new SelectDialog<>(this);
        dialog.setTip("选择存储类型");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<StorageDriveType.TYPE>() {
            @Override
            public void click(StorageDriveType.TYPE value, int pos) {
                dialog.dismiss();
                if (value == StorageDriveType.TYPE.LOCAL) {
                    addLocalDrive();
                } else if (value == StorageDriveType.TYPE.WEBDAV) {
                    showConfigDialog("添加 WebDAV", StorageDriveType.TYPE.WEBDAV, null);
                } else if (value == StorageDriveType.TYPE.SMB) {
                    showConfigDialog("添加 SMB 共享", StorageDriveType.TYPE.SMB, null);
                } else if (value == StorageDriveType.TYPE.FTP) {
                    showConfigDialog("添加 FTP 服务器", StorageDriveType.TYPE.FTP, null);
                }
            }

            @Override
            public String getDisplay(StorageDriveType.TYPE val) {
                String[] names = StorageDriveType.getTypeNames();
                return val.ordinal() < names.length ? names[val.ordinal()] : val.name();
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
        // 扫描 /storage/ 下所有挂载点（包含U盘等外置存储）
        java.util.ArrayList<String> paths = new java.util.ArrayList<>();
        paths.add("/storage/emulated/0/");
        paths.add("/storage/emulated/0/Download/");
        paths.add("/storage/emulated/0/Movies/");
        paths.add("/storage/emulated/0/DCIM/");
        // 扫描外置U盘/存储卡
        try {
            java.io.File storage = new java.io.File("/storage/");
            java.io.File[] mounts = storage.listFiles();
            if (mounts != null) {
                for (java.io.File m : mounts) {
                    String name = m.getName();
                    // 排除内部存储和已知系统目录
                    if (name.equals("emulated") || name.equals("self") || name.startsWith(".")) continue;
                    if (m.isDirectory()) {
                        paths.add(m.getAbsolutePath() + "/");
                    }
                }
            }
        } catch (Exception ignored) {}
        paths.add("自定义路径...");
        final String[] commonPaths = paths.toArray(new String[0]);

        SelectDialog<String> dialog = new SelectDialog<>(this);
        dialog.setTip("选择本地目录");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                if (pos < commonPaths.length - 1) {
                    RoomDataManger.insertDriveRecord(value, StorageDriveType.TYPE.LOCAL.ordinal(), null);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                } else {
                    // 自定义路径 — 使用config dialog
                    showConfigDialog("添加本地路径", StorageDriveType.TYPE.LOCAL, null);
                }
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        }, Arrays.asList(commonPaths), 0);
        dialog.show();
    }

    private void showEditDialog(DriveFolderFile item) {
        StorageDrive sd = item.getDriveData();
        if (sd == null) return;
        StorageDriveType.TYPE type = StorageDriveType.TYPE.values()[sd.driveType];
        String[] typeNames = StorageDriveType.getTypeNames();
        String typeLabel = sd.driveType < typeNames.length ? typeNames[sd.driveType] : "";
        showConfigDialog("编辑 " + typeLabel, type, sd);
    }

    private void showConfigDialog(String title, StorageDriveType.TYPE type, StorageDrive existing) {
        BaseDialog dialog = new BaseDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_drive_config, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        tvTitle.setText(title);

        LinearLayout fieldContainer = view.findViewById(R.id.fieldContainer);
        List<EditText> inputs = new ArrayList<>();
        List<String> fieldKeys = new ArrayList<>();

        // 先添加自定义名称字段
        View nameField = getLayoutInflater().inflate(R.layout.item_drive_config_field, null);
        TextView nameLabel = nameField.findViewById(R.id.tvLabel);
        EditText nameInput = nameField.findViewById(R.id.etInput);
        nameLabel.setText("名称");
        nameInput.setHint("自定义名称（可选）");
        if (existing != null && existing.name != null) {
            nameInput.setText(existing.name);
        }
        fieldContainer.addView(nameField);
        inputs.add(nameInput);
        fieldKeys.add("_name");

        // 根据类型生成配置字段
        String[][] fields = getConfigFields(type);
        for (String[] field : fields) {
            View fieldView = getLayoutInflater().inflate(R.layout.item_drive_config_field, null);
            TextView label = fieldView.findViewById(R.id.tvLabel);
            EditText input = fieldView.findViewById(R.id.etInput);
            label.setText(field[0]);
            input.setHint(field[1]);
            if (field.length > 2) {
                input.setText(field[2]);
            }
            if (field[0].contains("密") || field[0].contains("密码")) {
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            fieldContainer.addView(fieldView);
            inputs.add(input);
            fieldKeys.add(field[0]);
        }

        // 已有配置：填入值
        if (existing != null && existing.configJson != null) {
            try {
                com.google.gson.JsonObject cfg = com.google.gson.JsonParser.parseString(existing.configJson).getAsJsonObject();
                for (int i = 1; i < inputs.size() && i < fieldKeys.size(); i++) {
                    String key = fieldKeys.get(i);
                    if (cfg.has(key)) {
                        inputs.get(i).setText(cfg.get(key).getAsString());
                    }
                }
            } catch (Exception ignored) {}
        }

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            // 收集自定义名称
            String customName = inputs.get(0).getText().toString().trim();

            // 收集配置字段
            JsonObject config = new JsonObject();
            for (int i = 1; i < inputs.size() && i < fieldKeys.size(); i++) {
                String val = inputs.get(i).getText().toString().trim();
                String key = fieldKeys.get(i);
                if (val.isEmpty() && key.contains("地址")) {
                    Toast.makeText(mContext, "请填写" + key, Toast.LENGTH_SHORT).show();
                    return;
                }
                config.addProperty(key, val);
            }

            // 名称：用自定义名称或自动生成
            String name = customName.isEmpty() ? title : customName;

            if (existing != null) {
                RoomDataManger.updateDriveConfig(existing.id, name, type.ordinal(), config.toString());
            } else {
                RoomDataManger.insertDriveRecord(name, type.ordinal(), config.toString());
            }
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private String[][] getConfigFields(StorageDriveType.TYPE type) {
        switch (type) {
            case WEBDAV:
                return new String[][]{
                        {"服务器地址", "https://example.com/webdav"},
                        {"用户名", ""},
                        {"密码", ""}
                };
            case SMB:
                return new String[][]{
                        {"服务器地址", "smb://192.168.1.100/share"},
                        {"用户名", ""},
                        {"密码", ""},
                        {"域", "WORKGROUP"}
                };
            case FTP:
                return new String[][]{
                        {"主机地址", "192.168.1.100"},
                        {"端口", "21"},
                        {"用户名", ""},
                        {"密码", ""}
                };
            case LOCAL:
                return new String[][]{
                        {"路径", "/storage/emulated/0/"}
                };
            default:
                return new String[][]{{"地址", ""}};
        }
    }

    // 从 configJson 中提取服务器 URL
    private String getDriveUrl(StorageDrive drive) {
        if (drive == null || drive.configJson == null || drive.configJson.isEmpty()) {
            return drive != null ? drive.name : "";
        }
        try {
            com.google.gson.JsonObject cfg = com.google.gson.JsonParser.parseString(drive.configJson).getAsJsonObject();
            if (cfg.has("路径")) return cfg.get("路径").getAsString();
            if (cfg.has("服务器地址")) return cfg.get("服务器地址").getAsString();
            if (cfg.has("主机地址")) return cfg.get("主机地址").getAsString();
        } catch (Exception ignored) {}
        return drive.name;
    }

    private void playFile(DriveFolderFile file) {
        String playUrl;
        StorageDrive driveData = viewModel.getCurrentDrive() != null ? viewModel.getCurrentDrive().getDriveData() : null;
        String driveUrl = getDriveUrl(driveData);
        String pathSuffix = viewModel.getCurrentDriveNote().getAccessingPathStr()
                + viewModel.getCurrentDriveNote().name + "/" + file.name;

        if (file.getDriveType() == StorageDriveType.TYPE.LOCAL) {
            String localPath = driveUrl;
            if (!localPath.endsWith("/")) localPath += "/";
            playUrl = "file://" + localPath + pathSuffix;
        } else if (file.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
            playUrl = driveUrl + "/" + pathSuffix;
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(playUrl);
        } else if (file.getDriveType() == StorageDriveType.TYPE.SMB) {
            String smbPath = driveUrl;
            if (!smbPath.endsWith("/")) smbPath += "/";
            String smbUrl = smbPath + pathSuffix;
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(smbUrl);
        } else if (file.getDriveType() == StorageDriveType.TYPE.FTP) {
            String ftpUrl = "ftp://" + driveUrl + "/" + pathSuffix;
            playUrl = com.github.tvbox.osc.util.LocalProxyServer.getInstance().proxyUrl(ftpUrl);
        } else {
            playUrl = file.name;
        }

        com.github.tvbox.osc.bean.VodInfo vodInfo = new com.github.tvbox.osc.bean.VodInfo();
        vodInfo.id = "drive_" + System.currentTimeMillis();
        vodInfo.name = file.name;
        vodInfo.sourceKey = "drive";

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
        intent.putExtra("sourceKey", "drive");
        startActivity(intent);
    }

    private void initData() {
        tvTitle.setText("文件管理器");
        drives.clear();
        List<StorageDrive> storageDrives = RoomDataManger.getAllDrives();
        for (StorageDrive sd : storageDrives) {
            DriveFolderFile drive = new DriveFolderFile(sd);
            drives.add(drive);
        }
        adapter.setNewData(drives);
        btnAddServer.setVisibility(View.VISIBLE);
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
