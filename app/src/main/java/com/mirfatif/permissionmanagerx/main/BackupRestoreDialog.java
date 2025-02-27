package com.mirfatif.permissionmanagerx.main;

import static com.mirfatif.permissionmanagerx.util.ApiUtils.getString;

import android.net.Uri;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import com.mirfatif.permissionmanagerx.R;
import com.mirfatif.permissionmanagerx.backup.BackupFileSelector;
import com.mirfatif.permissionmanagerx.backup.BackupRestore;
import com.mirfatif.permissionmanagerx.backup.BackupRestore.Result;
import com.mirfatif.permissionmanagerx.base.AlertDialogFragment;
import com.mirfatif.permissionmanagerx.databinding.BackupRestoreDialogBinding;
import com.mirfatif.permissionmanagerx.databinding.ProgressDialogBinding;
import com.mirfatif.permissionmanagerx.util.ApiUtils;
import com.mirfatif.permissionmanagerx.util.StringUtils;
import com.mirfatif.permissionmanagerx.util.Utils;
import com.mirfatif.permissionmanagerx.util.bg.LiveTasksQueueTyped;
import com.mirfatif.privtasks.util.MyLog;
import java.util.Objects;

public class BackupRestoreDialog {

  private static final String TAG = "BackupRestoreDialog";

  private final MainActivity mA;

  public BackupRestoreDialog(MainActivity activity) {
    mA = activity;
  }

  private BackupFileSelector mBackupLauncher;
  private BackupFileSelector mRestoreLauncher;

  public void onCreated() {
    mBackupLauncher = new BackupFileSelector(mA.mA, true, uri -> doBackupRestore(true, uri));
    mRestoreLauncher = new BackupFileSelector(mA.mA, false, uri -> doBackupRestore(false, uri));
  }

  private CharSequence mSwapUserIdFrom, mSwapUserIdTo;
  private boolean mSkipUninstalledApps = false, mSwapUserIds = false;

  public AlertDialog createDialog() {
    BackupRestoreDialogBinding b = BackupRestoreDialogBinding.inflate(mA.mA.getLayoutInflater());

    CheckBox cb = b.skipUninstalledPackages;
    cb.setChecked(mSkipUninstalledApps);
    cb.setOnClickListener(v -> mSkipUninstalledApps = cb.isChecked());

    if (!Utils.isFreeVersion()) {
      b.swapUserIds.setVisibility(View.VISIBLE);

      b.swapUserIds.setOnClickListener(
          v -> {
            mSwapUserIds = b.swapUserIds.isChecked();
            b.userIdsCont.setVisibility(mSwapUserIds ? View.VISIBLE : View.GONE);
          });

      b.userIdFrom.addTextChangedListener((UserIdWatcher) s -> mSwapUserIdFrom = s);
      b.userIdTo.addTextChangedListener((UserIdWatcher) s -> mSwapUserIdTo = s);
    }

    return new Builder(mA.mA)
        .setPositiveButton(R.string.backup, (d, w) -> mBackupLauncher.launch())
        .setNegativeButton(R.string.restore, (d, w) -> mRestoreLauncher.launch())
        .setTitle(getString(R.string.backup) + " / " + getString(R.string.restore))
        .setView(b.getRoot())
        .create();
  }

  private interface UserIdWatcher extends TextWatcher {

    default void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    default void onTextChanged(CharSequence s, int start, int before, int count) {}
  }

  private void doBackupRestore(boolean isBackup, Uri uri) {
    if (uri == null) {

      return;
    }

    ProgressDialogBinding b = ProgressDialogBinding.inflate(mA.mA.getLayoutInflater());
    b.progText.setText(isBackup ? R.string.backup_in_progress : R.string.restore_in_progress);

    AlertDialog dialog =
        new Builder(mA.mA)
            .setTitle(isBackup ? R.string.backup : R.string.restore)
            .setPositiveButton(android.R.string.ok, null)
            .setView(b.getRoot())
            .create();

    AlertDialogFragment dialogFrag = AlertDialogFragment.create(dialog, "BACKUP_RESTORE");
    dialogFrag.setCancelable(false);

    dialog.setOnShowListener(
        d -> {
          dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

          new LiveTasksQueueTyped<>(
                  mA.mA,
                  () -> {
                    if (isBackup) {
                      return BackupRestore.INS.backupNoThrow(
                          uri, true, mSkipUninstalledApps, getSwapUserIds());
                    } else {
                      return BackupRestore.INS.restore(uri, mSkipUninstalledApps, getSwapUserIds());
                    }
                  })
              .onUiWith(result -> handleResult(isBackup, result, b, dialogFrag))
              .start();
        });

    dialogFrag.show(mA.mA);
  }

  private BackupRestore.SwapUserIds getSwapUserIds() {
    if (mSwapUserIds && mSwapUserIdFrom != null && mSwapUserIdTo != null) {
      try {
        return new BackupRestore.SwapUserIds(
            Integer.parseInt(mSwapUserIdFrom.toString().trim()),
            Integer.parseInt(mSwapUserIdTo.toString().trim()));
      } catch (NumberFormatException ignored) {
      }
    }

    return null;
  }

  private void handleResult(
      boolean isBackup, Result res, ProgressDialogBinding b, AlertDialogFragment dialog) {
    b.prog.setVisibility(View.GONE);
    Objects.requireNonNull(dialog.getDialog())
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .setEnabled(true);
    dialog.setCancelable(true);

    if (res == null) {
      MyLog.e(TAG, "handleResult", (isBackup ? "Backup" : "Restore") + " failed");
      b.progText.setText(R.string.backup_restore_failed);
    } else {
      String message =
          ApiUtils.getQtyString(R.plurals.backup_restore_processed_prefs, res.prefs, res.prefs);

      message =
          ApiUtils.getQtyString(
              R.plurals.backup_restore_processed_refs, res.perms, message, res.perms);

      if (res.invalidPrefs > 0) {
        message =
            ApiUtils.getQtyString(
                R.plurals.backup_restore_invalid_prefs,
                res.invalidPrefs,
                message,
                res.invalidPrefs);
      }

      if (res.skippedApps > 0) {
        message =
            ApiUtils.getQtyString(
                R.plurals.backup_restore_uninstalled_apps,
                res.skippedApps,
                message,
                res.skippedApps);
      }

      b.progText.setText(StringUtils.breakParas(message));

      if (!isBackup) {
        dialog.setOnDismissListener(d -> mA.mActFlavor.onRestoreDone());
      }
    }
  }
}
