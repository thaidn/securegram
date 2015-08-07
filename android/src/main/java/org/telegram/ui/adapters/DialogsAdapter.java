/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesController;
import org.telegram.android.support.widget.RecyclerView;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.cells.DialogCell;
import org.telegram.ui.cells.LoadingCell;

import java.util.ArrayList;

public class DialogsAdapter extends RecyclerView.Adapter {

  private Context mContext;
  private int dialogsType;
  private long openedDialogId;
  private int currentCount;

  private class Holder extends RecyclerView.ViewHolder {

    public Holder(View itemView) {
      super(itemView);
    }
  }

  public DialogsAdapter(Context context, int type) {
    mContext = context;
    dialogsType = type;
  }

  public void setOpenedDialogId(long id) {
    openedDialogId = id;
  }

  public boolean isDataSetChanged() {
    int current = currentCount;
    return current != getItemCount();
  }

  private ArrayList<TLRPC.TL_dialog> getDialogsArray() {
    if (dialogsType == 0) {
      return MessagesController.getInstance().dialogs;
    } else if (dialogsType == 1) {
      return MessagesController.getInstance().dialogsServerOnly;
    } else if (dialogsType == 2) {
      return MessagesController.getInstance().dialogsGroupsOnly;
    }
    return null;
  }

  @Override
  public int getItemCount() {
    int count = getDialogsArray().size();
    if (count == 0 && MessagesController.getInstance().loadingDialogs) {
      return 0;
    }
    if (!MessagesController.getInstance().dialogsEndReached) {
      count++;
    }
    currentCount = count;
    return count;
  }

  public TLRPC.TL_dialog getItem(int i) {
    ArrayList<TLRPC.TL_dialog> arrayList = getDialogsArray();
    if (i < 0 || i >= arrayList.size()) {
      return null;
    }
    return arrayList.get(i);
  }

  @Override
  public long getItemId(int i) {
    return i;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    View view = null;
    if (viewType == 0) {
      view = new DialogCell(mContext);
    } else if (viewType == 1) {
      view = new LoadingCell(mContext);
    }
    return new Holder(view);
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
    if (viewHolder.getItemViewType() == 0) {
      DialogCell cell = (DialogCell) viewHolder.itemView;
      cell.useSeparator = (i != getItemCount() - 1);
      TLRPC.TL_dialog dialog = getItem(i);
      if (dialogsType == 0) {
        if (AndroidUtilities.isTablet()) {
          cell.setDialogSelected(dialog.id == openedDialogId);
        }
      }
      cell.setDialog(dialog, i, dialogsType);
    }
  }

  @Override
  public int getItemViewType(int i) {
    if (i == getDialogsArray().size()) {
      return 1;
    }
    return 0;
  }
}
