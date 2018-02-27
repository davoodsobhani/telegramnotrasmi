/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package ir.mehdi.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import ir.mehdi.telegraf.HiddenController;
import ir.mehdi.messenger.AndroidUtilities;
import ir.mehdi.messenger.ApplicationLoader;
import ir.mehdi.messenger.MessagesController;
import ir.mehdi.messenger.support.widget.RecyclerView;
import ir.mehdi.tgnet.TLRPC;
import ir.mehdi.ui.Cells.DialogCell;
import ir.mehdi.ui.Components.RecyclerListView;

public class DialogsAdapter extends RecyclerListView.SelectionAdapter
{
	// telegraf
	public static final String UNREAD      = "unread";
	public static final String FAVOR       = "favor";
	public static final String BOT         = "bot";
	public static final String CHANNEL     = "channel";
	public static final String ALL         = "all";
	public static final String SUPPERGROUP = "sgroup";
	public static final String CONTACT     = "contact";
	public static final String GROUP       = "ngroup";

	// telegraf
	public static String         categoryId;
	public static DialogsAdapter Instance;
	public        boolean        Hiddenmode;

	//	private Context mContext;
	private int  dialogsType;
	private long openedDialogId;
	private int  currentCount;

	public DialogsAdapter(Context context, int type)
	{
		//		mContext = context;
		dialogsType = type;
	}

	public static DialogsAdapter getInstance()
	{
		if (Instance != null)
		{
			return Instance;
		}

		Instance = new DialogsAdapter(ApplicationLoader.applicationContext, 0);
		return Instance;
	}

	public void setOpenedDialogId(long id)
	{
		openedDialogId = id;
	}

	public boolean isDataSetChanged()
	{
		int     current = currentCount;
		boolean flag    = current != getItemCount() || current == 1;
		//		Log.d("_telegraf", "Adapter IsDataChanged: " + ((Boolean)flag).toString());
		return flag;
	}

	// telegraf
	@Override
	public void notifyDataSetChanged()
	{
		ApplicationLoader.SetHiddenMode(ApplicationLoader.applicationContext, Hiddenmode);
		super.notifyDataSetChanged();
	}

	public void setDialogsType(int dialogsType)
	{
		this.dialogsType = dialogsType;
	}

	// telegraf
	private ArrayList<TLRPC.TL_dialog> HidePRoccess(ArrayList<TLRPC.TL_dialog> ret)
	{
		ArrayList<TLRPC.TL_dialog> reth = new ArrayList<>();
		for (int i = 0; i < ret.size(); i++)
		{
			if (Hiddenmode)
			{
				if (HiddenController.isHidden(ret.get(i).id))
				{
					reth.add(ret.get(i));
				}
			}
			else
			{
				if (!HiddenController.isHidden(ret.get(i).id))
				{
					reth.add(ret.get(i));
				}
			}

		}

		return reth;
	}

	public ArrayList<TLRPC.TL_dialog> getDialogsArray()
	{
		//		MessagesController.getInstance().loadingDialogs = false; // telegraf

		ArrayList<TLRPC.TL_dialog> ret = new ArrayList<>();

		if (dialogsType == 0)
		{
			// telegraf
			switch (categoryId)
			{
				case ALL:
					ret = MessagesController.getInstance().dialogs;
					break;
				case CHANNEL:
					ret = MessagesController.getInstance().dialogsChannelOnly;
					break;
				case GROUP:
					ret = MessagesController.getInstance().dialogsGroupsOnly;
					break;
				case CONTACT:
					ret = MessagesController.getInstance().dialogsContactOnly;
					break;
				case FAVOR:
					ret = MessagesController.getInstance().dialogsFavoriteOnly;
					break;
				case BOT:
					ret = MessagesController.getInstance().dialogsBotOnly;
					break;
				case UNREAD:
					ret = MessagesController.getInstance().dialogsUnreadOnly;
					break;
			}
		}
		else if (dialogsType == 1)
		{
			ret = MessagesController.getInstance().dialogsServerOnly;
		}
		else if (dialogsType == 2)
		{
			ret = MessagesController.getInstance().dialogsGroupsOnly;
		}
		return HidePRoccess(ret);
	}

	@Override
	public int getItemCount()
	{
		int count = getDialogsArray().size();
		if (count == 0 && MessagesController.getInstance().loadingDialogs) // telegraf
		{
			return 0;
		}
		if (!MessagesController.getInstance().dialogsEndReached)
		{
			count++;
		}
		currentCount = count;

		//		Log.d("_telegraf", "Adapter GetItemCount: " + ((Integer)count).toString());

		return count;
	}

	public TLRPC.TL_dialog getItem(int i)
	{
		ArrayList<TLRPC.TL_dialog> arrayList = getDialogsArray();
		if (i < 0 || i >= arrayList.size())
		{
			return null;
		}
		return arrayList.get(i);
	}

	@Override
	public void onViewAttachedToWindow(RecyclerView.ViewHolder holder)
	{
		if (holder.itemView instanceof DialogCell)
		{
			((DialogCell) holder.itemView).checkCurrentDialogIndex();
		}
	}

	@Override
	public boolean isEnabled(RecyclerView.ViewHolder holder)
	{
		Boolean flag = holder.getItemViewType() != 1;
		//		Log.d("_telegraf", "Adapter IsEnabled: " + flag.toString());

		return flag;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType)
	{
		View view = null;
		if (viewType == 0)
		{
			view = new DialogCell(ApplicationLoader.applicationContext);
		}
		else if (viewType == 1)
		{
			view = new View(ApplicationLoader.applicationContext); // telegraf Changed LoadingCell to View
		}
		view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
		return new RecyclerListView.Holder(view);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i)
	{
		if (viewHolder.getItemViewType() == 0)
		{
			DialogCell cell = (DialogCell) viewHolder.itemView;
			cell.setHiddenmode(Hiddenmode); // telegraf
			cell.useSeparator = (i != getItemCount() - 1);
			TLRPC.TL_dialog dialog = getItem(i);
			if (dialogsType == 0)
			{
				if (AndroidUtilities.isTablet())
				{
					cell.setDialogSelected(dialog.id == openedDialogId);
				}
			}
			cell.setDialog(dialog, i, dialogsType, Hiddenmode); // telegraf
		}
	}

	@Override
	public int getItemViewType(int i)
	{
		if (i == getDialogsArray().size())
		{
			return 1;
		}
		return 0;
	}
}
