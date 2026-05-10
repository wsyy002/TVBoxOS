package xyz.doikki.videoplayer.exo;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSource.Factory;

/**
 * SMB 数据源工厂：为 smb:// URL 创建 SmbDataSource
 */
public class SmbDataSourceFactory implements Factory {

    @NonNull
    @Override
    public DataSource createDataSource() {
        Log.i("SmbDS", "Factory.createDataSource()");
        return new SmbDataSource();
    }
}
