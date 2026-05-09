package com.github.tvbox.osc.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        try {
            android.widget.TextView tvVersion = findViewById(com.github.tvbox.osc.R.id.tvVersion);
            if (tvVersion != null) {
                String v = getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0).versionName;
                tvVersion.setText("版本号: " + v);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}