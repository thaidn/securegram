package xyz.securegram.axolotl;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Created by thaidn on 11/11/15.
 */
public class ExportDatabaseFileTask extends AsyncTask<String, Void, Boolean> {
  // automatically done on worker thread (separate from UI thread)
  protected Boolean doInBackground(final String... args) {

    File dbFile =
        new File(Environment.getDataDirectory() + "/data/xyz.securegram/files/cache4.db");

    File exportDir = new File(Environment.getExternalStorageDirectory(), "");
    if (!exportDir.exists()) {
      exportDir.mkdirs();
    }
    File file = new File(exportDir, dbFile.getName());

    try {
      file.createNewFile();
      this.copyFile(dbFile, file);
      return true;
    } catch (IOException e) {
      Log.e("securegram", e.getMessage(), e);
      return false;
    }
  }

  void copyFile(File src, File dst) throws IOException {
    FileChannel inChannel = new FileInputStream(src).getChannel();
    FileChannel outChannel = new FileOutputStream(dst).getChannel();
    try {
      inChannel.transferTo(0, inChannel.size(), outChannel);
    } finally {
      if (inChannel != null)
        inChannel.close();
      if (outChannel != null)
        outChannel.close();
    }
  }

}

