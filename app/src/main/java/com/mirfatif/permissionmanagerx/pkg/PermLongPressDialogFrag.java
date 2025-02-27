package com.mirfatif.permissionmanagerx.pkg;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.FragmentManager;
import com.mirfatif.permissionmanagerx.R;
import com.mirfatif.permissionmanagerx.base.BottomSheetDialogFrag;
import com.mirfatif.permissionmanagerx.databinding.PermLongPressDialogBinding;
import com.mirfatif.permissionmanagerx.fwk.PackageActivityM;
import com.mirfatif.permissionmanagerx.parser.Package;
import com.mirfatif.permissionmanagerx.parser.PackageParser;
import com.mirfatif.permissionmanagerx.parser.Permission;
import com.mirfatif.permissionmanagerx.parser.PkgParserFlavor;
import com.mirfatif.permissionmanagerx.parser.permsdb.PermissionEntity;
import com.mirfatif.permissionmanagerx.parser.permsdb.PermsDb;
import com.mirfatif.permissionmanagerx.parser.permsdb.PermsDbFlavor;
import com.mirfatif.permissionmanagerx.prefs.ExcFiltersData;
import com.mirfatif.permissionmanagerx.prefs.MySettings;
import com.mirfatif.permissionmanagerx.util.StringUtils;
import com.mirfatif.permissionmanagerx.util.UiUtils;
import com.mirfatif.privtasks.util.bg.BgRunner;

public class PermLongPressDialogFrag extends BottomSheetDialogFrag {

  private final Permission mPerm;
  private final Package mPkg;
  private final PkgActivityFlavor mPkgActivityFlavor;

  PermLongPressDialogFrag(Permission perm, Package pkg, PkgActivityFlavor pkgActivityFlavor) {
    mPerm = perm;
    mPkg = pkg;
    mPkgActivityFlavor = pkgActivityFlavor;
  }

  public PermLongPressDialogFrag() {
    this(null, null, null);
  }

  private PackageActivityM mA;

  public void onAttach(Context context) {
    super.onAttach(context);
    mA = (PackageActivityM) getActivity();
  }

  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    if (mPerm == null || mPkg == null) {
      return null;
    }

    PermLongPressDialogBinding b = PermLongPressDialogBinding.inflate(mA.getLayoutInflater());

    CharSequence label = PkgParserFlavor.INS.getPermName(mPerm);

    b.permLabelV.setText(label);
    b.permLabelV.setSelected(true);

    if (!label.equals(mPerm.getName())) {
      b.permNameV.setText(mPerm.getName());
      b.permNameV.setSelected(true);
      b.permNameV.setVisibility(View.VISIBLE);
    }

    if (ExcFiltersData.INS.canBeExcluded(mPerm)) {
      b.excludePerm.setOnClickListener(
          v -> {
            dismissAllowingStateLoss();
            BgRunner.execute(this::excludePerm);
          });
    } else {
      b.excludePerm.setEnabled(false);
    }

    if (mPerm.isChangeable()) {
      boolean isReferenced = Boolean.TRUE.equals(mPerm.isReferenced());
      String permState = mPerm.refString();

      if (isReferenced) {
        b.refButton.setText(R.string.clear_reference);
      } else {
        String permStateStr;
        if (mPerm.isAppOp()) {
          permStateStr = PermissionAdapter.getLocalizedMode(permState);
        } else if (permState.equals(Permission.GRANTED)) {
          permStateStr = getString(R.string.perm_mode_granted);
        } else {
          permStateStr = getString(R.string.perm_mode_revoked);
        }
        b.refButton.setText(
            StringUtils.htmlToString(getString(R.string.set_perm_state_reference, permStateStr)));
      }

      b.refButton.setOnClickListener(
          v -> {
            dismissAllowingStateLoss();
            BgRunner.execute(() -> setOrClearRef(isReferenced, permState));
          });
    } else {
      b.refButton.setText(R.string.set_reference);
      b.refButton.setEnabled(false);
    }

    return b.getRoot();
  }

  public Dialog onCreateDialog(Bundle savedInstanceState) {
    if (mPerm == null) {

      dismissAllowingStateLoss();
    }
    return super.onCreateDialog(savedInstanceState);
  }

  private void excludePerm() {
    MySettings.INS.addPermToExcludedPerms(mPerm.getName());
    mA.mA.updatePkg();

    PackageParser.INS.updatePkgList();
  }

  private void setOrClearRef(boolean isReferenced, String permState) {
    if (isReferenced) {
      boolean isPerUid = MySettings.INS.useUniqueRefForAppOpUidMode() && mPerm.isPerUid();
      int userID = PermsDbFlavor.getUserIdForPermRefs(mPkg.getUid());

      PermsDb.INS
          .getDb()
          .deletePerm(mPkg.getName(), mPerm.getName(), mPerm.isAppOp(), isPerUid, userID);

      PermsDb.INS.updateRefs(
          mPkg.getName(), mPerm.getName(), null, mPerm.isAppOp(), isPerUid, userID);

      mPkgActivityFlavor.pkgRefChanged(mPkg);
      mA.mA.updatePkg();
    } else {
      setRef(mPkg, mPerm, permState, mPkgActivityFlavor);
      mA.mA.updatePkg();
    }
  }

  static void setRef(Package pkg, Permission perm, String state, PkgActivityFlavor actFlav) {
    boolean isPerUid = MySettings.INS.useUniqueRefForAppOpUidMode() && perm.isPerUid();
    int userID = PermsDbFlavor.getUserIdForPermRefs(pkg.getUid());

    PermissionEntity entity =
        new PermissionEntity(
            pkg.getName(), perm.getName(), state, perm.isAppOp(), isPerUid, userID);

    PermsDb.INS.updateRefsDb(entity);

    PermsDb.INS.updateRefs(pkg.getName(), perm.getName(), state, perm.isAppOp(), isPerUid, userID);
    actFlav.pkgRefChanged(pkg);
  }

  public static void show(
      Permission perm, Package pkg, PkgActivityFlavor pkgActivityFlavor, FragmentManager fm) {
    if (!ExcFiltersData.INS.canBeExcluded(perm) && !perm.isChangeable()) {
      UiUtils.showToast(R.string.no_action_available);
    } else {
      new PermLongPressDialogFrag(perm, pkg, pkgActivityFlavor).show(fm, "PERM_OPTIONS");
    }
  }
}
