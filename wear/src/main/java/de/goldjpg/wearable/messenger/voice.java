package de.goldjpg.wearable.messenger;

import android.app.Activity;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import com.arthenica.mobileffmpeg.FFmpeg;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import de.goldjpg.wearable.messenger.databinding.ActivityVoiceBinding;


public class voice extends Activity {

    MediaRecorder recorder = new MediaRecorder();
    ImageButton recordbut;
    TextView secondsview;
    Timer timer = new Timer();
    boolean recording = false;
    int recordseconds = 0;
    TdApi.Chat mychat;
    File recordfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityVoiceBinding binding = ActivityVoiceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mychat = MainActivity.client.chats.get(getIntent().getLongExtra("id", 0));

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recordfile = new File(getCacheDir() + "/" +Messages.generatetoken(10)+".mp4");
        recorder.setOutputFile(recordfile);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        recordbut = findViewById(R.id.recordbutton);
        recordbut.setOnClickListener(v -> {
            if(recording){
                recorder.stop();
                recordbut.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        File target = convert();
                        MainActivity.client.client.send(new TdApi.SendMessage(mychat.id, 0, 0, null, null, new TdApi.InputMessageVoiceNote(new TdApi.InputFileLocal(target.getPath()), recordseconds, new byte[0], null)), new Client.ResultHandler() {
                            @Override
                            public void onResult(TdApi.Object object) {
                                voice.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        finish();
                                    }
                                });
                            }
                        });
                    }
                }).start();
            }else{
                recording = true;
                recorder.start();
                recordbut.setImageResource(R.drawable.ic_baseline_pause_24);
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        if(recording){
                            voice.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    secondsview.setText(recordseconds + " seconds");
                                }
                            });
                        }
                        recordseconds += 1;
                    }

                },0,1000);
            }
        });
        secondsview = findViewById(R.id.recordtime);
    }

    void print(String s){
        Log.i("Messenger", s);
    }

    public File convert(){
        File tempfile = new File(getCacheDir() + "/" + recordfile.getName().split("\\.")[0] + ".ogg");
        tempfile.delete();
        String command = "-i "+recordfile.getPath()+" -c:a libopus " + tempfile.getPath();
        print(command);
        FFmpeg.execute(command);
        return tempfile;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(recording){
            recorder.release();
            timer.cancel();
        }
    }
}