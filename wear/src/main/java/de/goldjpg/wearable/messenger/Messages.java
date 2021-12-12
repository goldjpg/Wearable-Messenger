package de.goldjpg.wearable.messenger;

import android.Manifest;
import android.app.Activity;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.wearable.input.RemoteInputIntent;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;
import de.goldjpg.wearable.messenger.databinding.ActivityMessagesBinding;

public class Messages extends Activity {

    private ActivityMessagesBinding binding;
    WearableRecyclerView chatview;
    MessageHandler myhandler = new MessageHandler();
    MessageAdapter adapter = new MessageAdapter(new ArrayList<>());
    TdApi.Chat mychat;
    WearableLinearLayoutManager lmg;
    boolean loading = false;
    boolean end = false;
    TdApi.Message curplay = null;
    MediaPlayer mediaPlayer = null;

    public static int ACTION_MESSAGE_TEXT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mychat = MainActivity.client.chats.get(getIntent().getLongExtra("id", 0));
        binding = ActivityMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        chatview = findViewById(R.id.messages_view);
        chatview.setEdgeItemsCenteringEnabled(true);
        Chat_Activity.CustomScrollingLayoutCallback customScrollingLayoutCallback =
                new Chat_Activity.CustomScrollingLayoutCallback();
        lmg = new WearableLinearLayoutManager(this, customScrollingLayoutCallback);
        chatview.setLayoutManager(lmg);
        chatview.setAdapter(adapter);
        chatview.setHasFixedSize(false);
        chatview.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(lmg.findFirstCompletelyVisibleItemPosition() == 0){
                    if(!loading && !end){
                        loadMoreMessages();
                    }
                }
            }
        });
        if(mychat.permissions.canSendMessages){
            adapter.localDataSet.add("");
            adapter.notifyItemChanged(0);
            chatview.scrollToPosition(0);
        }
        MainActivity.client.mylistener.add(myhandler);
        loadMoreMessages();
    }

    public void SendMessage(){
        RemoteInput.Builder rm = new RemoteInput.Builder("Message").setLabel("Enter message...");

        Intent i = new Intent(RemoteInputIntent.ACTION_REMOTE_INPUT);
        i.putExtra(RemoteInputIntent.EXTRA_REMOTE_INPUTS,new RemoteInput[]{rm.build()});
        startActivityForResult(i, ACTION_MESSAGE_TEXT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == ACTION_MESSAGE_TEXT){
            try{
                Bundle bu = RemoteInput.getResultsFromIntent(data);
                if(bu != null){
                    String text = bu.getCharSequence("Message").toString();
                    MainActivity.client.sendMessage(mychat.id, text);
                }
            }catch (Exception e){e.printStackTrace();}

        }
    }

    public void SendMemo(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);
        }else{
            Intent i = new Intent(this, voice.class);
            i.putExtra("id", mychat.id);
            startActivity(i);
        }
    }

    public static String generatetoken(int chars) {
        StringBuilder mytok = new StringBuilder();
        Random myrand = new Random();
        for(int i=0;i<chars;i++) {
            mytok.append(myrand.nextInt(10));
        }
        return mytok.toString();
    }

    public File convert(File in){
        File tempfile = new File(getCacheDir() + "/" + in.getName().split("\\.")[0] + ".mp3");
        String command = "-i "+in.getPath()+" " + tempfile.getPath();
        print(command);
        if(!tempfile.exists()){
            FFmpeg.execute(command);
        }
        return tempfile;
    }

    public void playmemo(TdApi.Message message){
        if(curplay == null){
            if(message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR){
                TdApi.MessageVoiceNote cont = (TdApi.MessageVoiceNote) message.content;
                if(cont.voiceNote.voice.local.isDownloadingCompleted){
                    print("Starting to play file...");
                    curplay = message;
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setAudioAttributes(
                            new AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                    );
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                File newfile = convert(new File(cont.voiceNote.voice.local.path));
                                mediaPlayer.setDataSource(getApplicationContext(), Uri.fromFile(newfile));
                                mediaPlayer.prepare();
                                mediaPlayer.start();
                                print("Sound success" + mediaPlayer.getDuration());
                                UpdateMessage(message.id);
                            } catch (Exception e) {
                                e.printStackTrace();
                                print("Sound error");
                                curplay = null;
                            }
                        }
                    }).start();
                }else{
                    Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
                    if(cont.voiceNote.voice.local.canBeDownloaded && !cont.voiceNote.voice.local.isDownloadingActive){
                        MainActivity.client.client.send(new TdApi.DownloadFile(cont.voiceNote.voice.id, 5, cont.voiceNote.voice.local.downloadOffset, 0, false), new Client.ResultHandler() {
                            @Override
                            public void onResult(TdApi.Object object) {
                                UpdateMessage(message.id);
                            }
                        });
                        UpdateMessage(message.id);
                    }
                }
            }
        }else if (message == curplay){
            if(mediaPlayer.isPlaying()){
                mediaPlayer.pause();
                print("Pause file...");
            }else{
                mediaPlayer.start();
                print("Resume file...");
            }
            UpdateMessage(message.id);
        }else{
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            UpdateMessage(curplay.id);
            curplay = null;
            print("Play new file...");
            playmemo(message);
        }
    }

    public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Object> localDataSet;

        /**
         * Provide a reference to the type of views that you are using
         * (custom ViewHolder).
         */
        public class MessageViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TdApi.Message message;

            LinearLayout messageView;
            TextView msgView;
            TextView author;
            ImageButton voicebutton;
            ImageView myimage;
            TextView minimsg;

            public MessageViewHolder(View view) {
                super(view);
                messageView = view.findViewById(R.id.messageView);
                msgView = view.findViewById(R.id.msgr);
                author = view.findViewById(R.id.autortext);
                voicebutton = view.findViewById(R.id.playbut);
                myimage = view.findViewById(R.id.imgview);
                minimsg = view.findViewById(R.id.minimsg);
                voicebutton.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                playmemo(message);
            }
        }

        public class ActionsViewHolder extends RecyclerView.ViewHolder {


            public ActionsViewHolder(View view) {
                super(view);
                ImageButton keyboardbutton = view.findViewById(R.id.keyboardbutton);
                keyboardbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Messages.this.SendMessage();
                    }
                });
                ImageButton voicebutton = view.findViewById(R.id.voicebutton);
                voicebutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Messages.this.SendMemo();
                    }
                });
            }
        }

        public MessageAdapter(List<Object> localDataSet) {
            this.localDataSet = localDataSet;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            // Create a new view, which defines the UI of the list item

            if(viewType == 0){
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.chatmessage, viewGroup, false);
                return new MessageViewHolder(view);
            }else {
                View view = LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.chatactions, viewGroup, false);
                return new ActionsViewHolder(view);
            }
        }


        @Override
        public int getItemViewType(int position) {
           if(position == localDataSet.size()-1 && mychat.permissions.canSendMessages){
               return 1;
           }
           return 0;
        }
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder view, final int position) {
            Object ob = localDataSet.get(position);
            if(ob instanceof TdApi.Message){
                TdApi.Message me = (TdApi.Message) localDataSet.get(position);
                print("Update " + me.id + "|" + position);
                MessageViewHolder viewHolder = (MessageViewHolder) view;
                viewHolder.message = me;
                viewHolder.voicebutton.setVisibility(View.GONE);
                viewHolder.msgView.setVisibility(View.GONE);
                viewHolder.author.setVisibility(View.GONE);
                viewHolder.myimage.setVisibility(View.GONE);
                viewHolder.minimsg.setVisibility(View.GONE);
                if(me.sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR){
                    viewHolder.messageView.setGravity(Gravity.CENTER);
                }else if(me.sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR){
                    viewHolder.author.setVisibility(View.VISIBLE);
                    if(me.isOutgoing){
                        viewHolder.messageView.setGravity(Gravity.RIGHT);
                        viewHolder.author.setText(datetoString(me.date));
                    }else{
                        viewHolder.messageView.setGravity(Gravity.LEFT);
                        TdApi.MessageSenderUser user = (TdApi.MessageSenderUser) me.sender;
                        String username = "unknown";
                        try{
                            TdApi.User u = MainActivity.client.users.get(user.userId);
                            username = u.firstName;
                        }catch (Exception ignored){}
                        viewHolder.author.setText(username+" "+datetoString(me.date));
                    }
                }
                if(me.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR){
                    TdApi.MessageText cont = (TdApi.MessageText) me.content;
                    viewHolder.msgView.setVisibility(View.VISIBLE);
                    viewHolder.msgView.setText(cont.text.text);
                }else if(me.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR){
                    viewHolder.voicebutton.setVisibility(View.VISIBLE);
                    TdApi.MessageVoiceNote cont = (TdApi.MessageVoiceNote) me.content;
                    if(cont.voiceNote.voice.local.isDownloadingCompleted){
                        viewHolder.voicebutton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                        if(curplay != null && mediaPlayer != null){
                            if(curplay.id == me.id && mediaPlayer.isPlaying()){
                                viewHolder.voicebutton.setImageResource(R.drawable.ic_baseline_pause_24);
                            }
                        }
                    }else if(cont.voiceNote.voice.local.isDownloadingActive){
                        viewHolder.voicebutton.setImageResource(R.drawable.ic_baseline_cached_24);
                    }else{
                        viewHolder.voicebutton.setImageResource(R.drawable.ic_baseline_arrow_downward_24);
                    }
                    if(cont.caption.text.length() > 0){
                        viewHolder.minimsg.setVisibility(View.VISIBLE);
                        viewHolder.minimsg.setText(cont.caption.text);
                    }
                }else if(me.content.getConstructor() == TdApi.MessagePhoto.CONSTRUCTOR){
                    viewHolder.myimage.setVisibility(View.VISIBLE);
                    TdApi.MessagePhoto cont = (TdApi.MessagePhoto) me.content;
                    TdApi.File myfile = getNormalPhoto(cont.photo.sizes, "m");
                    if(myfile.local.isDownloadingCompleted){
                        viewHolder.myimage.setImageBitmap(BitmapFactory.decodeFile(myfile.local.path));
                    }else{
                        if(myfile.local.canBeDownloaded && !myfile.local.isDownloadingActive){
                            MainActivity.client.client.send(new TdApi.DownloadFile(myfile.id, 15, myfile.local.downloadOffset, 0, false), new Client.ResultHandler() {
                                @Override
                                public void onResult(TdApi.Object object) {
                                    UpdateMessage(myfile.id);
                                }
                            });
                        }
                        if(cont.photo.minithumbnail != null){
                            viewHolder.myimage.setImageBitmap(BitmapFactory.decodeByteArray(cont.photo.minithumbnail.data, 0, cont.photo.minithumbnail.data.length));
                        }else{
                            viewHolder.myimage.setImageResource(R.drawable.ic_baseline_cached_24);
                        }
                    }
                    if(cont.caption.text.length() > 0){
                        viewHolder.minimsg.setVisibility(View.VISIBLE);
                        viewHolder.minimsg.setText(cont.caption.text);
                    }
                }else{
                    viewHolder.msgView.setVisibility(View.VISIBLE);
                    viewHolder.msgView.setText("unknown message");
                }
            }
        }

        @Override
        public int getItemCount() {
            return localDataSet.size();
        }
    }

    public void UpdateMessage(long id){
        if(Looper.myLooper() == Looper.getMainLooper()){
            UpdateM(id);
        }else{
            Messages.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UpdateM(id);
                }
            });
        }
    }
    private void UpdateM(long id){
        for(Object me:adapter.localDataSet){
            if(me instanceof TdApi.Message){
                TdApi.Message curob = (TdApi.Message) me;
                if(curob.id == id){
                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(me));
                    chatview.scrollBy(0, 1);
                    return;
                }
            }
        }
    }

    public TdApi.File getNormalPhoto(TdApi.PhotoSize[] sizes, String type){
        for(TdApi.PhotoSize s:sizes){
            if(s.type.equals(type)){
                return s.photo;
            }
        }
        return sizes[0].photo;
    }

    void print(String s){
        Log.i("Messenger", s);
    }

    void loadMoreMessages(){
        long lastmessage = 0;
        loading = true;
        try{
            if(adapter.localDataSet.size() > 0){
                lastmessage = ((TdApi.Message)adapter.localDataSet.get(0)).id;
            }
        }catch (Exception ignored){}

        MainActivity.client.client.send(new TdApi.GetChatHistory(mychat.id, lastmessage, 0, 30, false), new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.Object object) {
                if(object.getConstructor() == TdApi.Messages.CONSTRUCTOR){
                    TdApi.Messages messages = (TdApi.Messages) object;
                    print("Received "+messages.totalCount+" messages");
                    if(messages.totalCount == 0){
                        end = true;
                    }else{
                        List<TdApi.Message> me = Arrays.asList(messages.messages);
                        Collections.reverse(me);
                        int curscroll = lmg.findFirstCompletelyVisibleItemPosition();
                        adapter.localDataSet.addAll(0,me);
                        adapter.notifyItemRangeChanged(0, messages.totalCount);
                        chatview.scrollToPosition(curscroll+messages.totalCount);
                    }
                }else{
                    print("Failed to receive messages");
                }
                loading = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainActivity.client.mylistener.remove(myhandler);
    }

    public String datetoString(long date){
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date*1000);
    }

    private class MessageHandler implements TGAPI.ActionListener {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateNewMessage.CONSTRUCTOR: {
                    print("New message");
                    TdApi.UpdateNewMessage update = (TdApi.UpdateNewMessage) object;
                    if(update.message.chatId == mychat.id){
                        adapter.localDataSet.add(adapter.getItemCount()-1,update.message);
                        adapter.notifyItemRangeChanged(adapter.getItemCount()-2, 2);
                    }
                    break;
                }case TdApi.UpdateMessageContent.CONSTRUCTOR: {
                    print("New message content");
                    TdApi.UpdateMessageContent update = (TdApi.UpdateMessageContent) object;
                    if(update.chatId == mychat.id){
                        for(Object me:adapter.localDataSet){
                            if(me instanceof TdApi.Message){
                                TdApi.Message curob = (TdApi.Message) me;
                                if(curob.id == update.messageId){
                                    curob.content = update.newContent;
                                    adapter.notifyItemChanged(adapter.localDataSet.indexOf(me));
                                    chatview.invalidate();
                                    break;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
}