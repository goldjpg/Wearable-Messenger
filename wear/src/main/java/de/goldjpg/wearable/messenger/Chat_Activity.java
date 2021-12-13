package de.goldjpg.wearable.messenger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;
import de.goldjpg.wearable.messenger.databinding.ActivityChatBinding;

public class Chat_Activity extends Activity {

    WearableRecyclerView chatview;
    ChatHandler myhandler = new ChatHandler();
    ChatAdapter adapter = new ChatAdapter(new ArrayList<TdApi.Chat>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityChatBinding binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        chatview = findViewById(R.id.chats_view);
        chatview.setEdgeItemsCenteringEnabled(true);
        CustomScrollingLayoutCallback customScrollingLayoutCallback =
                new CustomScrollingLayoutCallback();
        chatview.setLayoutManager(
                new WearableLinearLayoutManager(this, customScrollingLayoutCallback));
        chatview.setAdapter(adapter);
        MainActivity.client.mylistener.add(myhandler);
        reloadchats();
        if(!MainActivity.client.haveFullMainChatList){
            MainActivity.client.getMainChatList(30);
        }
        print("Chat Activity started");
    }

    public void reloadchats(){
        adapter.localDataSet.clear();
        synchronized (MainActivity.client.chats){
            synchronized (MainActivity.client.mainChatList){
                java.util.Iterator<TGAPI.OrderedChat> iter = MainActivity.client.mainChatList.iterator();
                for (int i = 0;  i < MainActivity.client.mainChatList.size(); i++) {
                    long chatId = iter.next().chatId;
                    TdApi.Chat chat = MainActivity.client.chats.get(chatId);
                    adapter.localDataSet.add(chat);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        private final List<TdApi.Chat> localDataSet;

        /**
         * Provide a reference to the type of views that you are using
         * (custom ViewHolder).
         */
        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            public final ImageView chatImage;
            public final TextView chatTitle;
            public final TextView chatmessage;
            public final TextView chatcount;
            public final TextView chattime;
            public long chatid;

            public ViewHolder(View view) {
                super(view);
                // Define click listener for the ViewHolder's View

                chatImage = (ImageView) view.findViewById(R.id.chat_image);
                chatTitle = (TextView) view.findViewById(R.id.chat_title);
                chatmessage = (TextView) view.findViewById(R.id.chat_last_message);
                chatcount = (TextView) view.findViewById(R.id.chat_last_messages_count);
                chattime = (TextView) view.findViewById(R.id.chat_last_message_time);
                View root = view.findViewById(R.id.chat_row_root);
                root.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                Chat_Activity.this.clickChat(chatid);
            }
        }

        public ChatAdapter(List<TdApi.Chat> localDataSet) {
            this.localDataSet = localDataSet;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            // Create a new view, which defines the UI of the list item
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_row_item, viewGroup, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            TdApi.Chat cur = localDataSet.get(position);
            if(cur.title.length() > 0){
                viewHolder.chatTitle.setText(cur.title);
            }else{
                viewHolder.chatTitle.setText("unknown");
            }
            if(cur.photo != null){
                if(cur.photo.small.local.isDownloadingCompleted){
                    viewHolder.chatImage.setImageBitmap(BitmapFactory.decodeFile(cur.photo.small.local.path));
                }else{
                    viewHolder.chatImage.setImageResource(R.drawable.ic_baseline_cached_24);
                }
            }else{
                viewHolder.chatImage.setImageResource(R.drawable.ic_baseline_chat_24);
            }
            if(cur.unreadCount > 0){
                viewHolder.chatcount.setVisibility(View.VISIBLE);
                viewHolder.chatcount.setText(cur.unreadCount + "");
            }else{
                viewHolder.chatcount.setVisibility(View.GONE);
            }
            viewHolder.chatmessage.setText("");
            viewHolder.chattime.setText("");
            if(cur.lastMessage != null){
                if(cur.lastMessage.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR){
                    TdApi.MessageText cont = (TdApi.MessageText) cur.lastMessage.content;
                    viewHolder.chatmessage.setText(cont.text.text);
                }
                viewHolder.chattime.setText(Messages.datetoString(cur.lastMessage.date));
            }
            viewHolder.chatid = cur.id;
        }

        @Override
        public int getItemCount() {
            return localDataSet.size();
        }
    }

    public void clickChat(long id){
        Intent i = new Intent(this, Messages.class);
        i.putExtra("id", id);
        startActivity(i);
    }

    void print(String s){
        Log.i("Messenger", s);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        print("Destroy");
        MainActivity.client.mylistener.remove(myhandler);
    }

    public static class CustomScrollingLayoutCallback extends WearableLinearLayoutManager.LayoutCallback {
        /** How much should we scale the icon at most. */
        private static final float MAX_ICON_PROGRESS = 0.65f;

        @Override
        public void onLayoutFinished(View child, RecyclerView parent) {

            // Figure out % progress from top to bottom
            float centerOffset = ((float) child.getHeight() / 2.0f) / (float) parent.getHeight();
            float yRelativeToCenterOffset = (child.getY() / parent.getHeight()) + centerOffset;

            // Normalize for center
            float progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
            // Adjust to the maximum scale
            progressToCenter = Math.min(progressToCenter, MAX_ICON_PROGRESS);

            child.setScaleX(1 - progressToCenter);
            child.setScaleY(1 - progressToCenter);

        }
    }

    private class ChatHandler implements TGAPI.ActionListener {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateNewChat.CONSTRUCTOR: {
                    TdApi.UpdateNewChat updateNewChat = (TdApi.UpdateNewChat) object;
                    TdApi.Chat chat = updateNewChat.chat;
                    adapter.localDataSet.add(0, chat);
                    break;
                }
                case TdApi.UpdateChatTitle.CONSTRUCTOR: {
                    TdApi.UpdateChatTitle updateChat = (TdApi.UpdateChatTitle) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatPhoto.CONSTRUCTOR: {
                    TdApi.UpdateChatPhoto updateChat = (TdApi.UpdateChatPhoto) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatLastMessage.CONSTRUCTOR: {
                    TdApi.UpdateChatLastMessage updateChat = (TdApi.UpdateChatLastMessage) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatPosition.CONSTRUCTOR: {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reloadchats();
                        }
                    });
                    break;
                }
                case TdApi.UpdateChatReadInbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadInbox updateChat = (TdApi.UpdateChatReadInbox) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatReadOutbox.CONSTRUCTOR: {
                    TdApi.UpdateChatReadOutbox updateChat = (TdApi.UpdateChatReadOutbox) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatUnreadMentionCount.CONSTRUCTOR: {
                    TdApi.UpdateChatUnreadMentionCount updateChat = (TdApi.UpdateChatUnreadMentionCount) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateMessageMentionRead.CONSTRUCTOR: {
                    TdApi.UpdateMessageMentionRead updateChat = (TdApi.UpdateMessageMentionRead) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(updateChat.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatNotificationSettings.CONSTRUCTOR: {
                    TdApi.UpdateChatNotificationSettings update = (TdApi.UpdateChatNotificationSettings) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(update.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR: {
                    TdApi.UpdateChatIsMarkedAsUnread update = (TdApi.UpdateChatIsMarkedAsUnread) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(update.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatIsBlocked.CONSTRUCTOR: {
                    TdApi.UpdateChatIsBlocked update = (TdApi.UpdateChatIsBlocked) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(update.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }
                case TdApi.UpdateChatHasScheduledMessages.CONSTRUCTOR: {
                    TdApi.UpdateChatHasScheduledMessages update = (TdApi.UpdateChatHasScheduledMessages) object;
                    TdApi.Chat chat = MainActivity.client.chats.get(update.chatId);
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                    break;
                }case TdApi.UpdateFile.CONSTRUCTOR: {
                    TdApi.UpdateFile update = (TdApi.UpdateFile) object;
                    for(TdApi.Chat chat:MainActivity.client.chats.values()){
                        if(chat.photo != null && update.file.local.isDownloadingCompleted){
                            if(chat.photo.small.id == update.file.id){
                                adapter.notifyItemChanged(adapter.localDataSet.indexOf(chat));
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
}