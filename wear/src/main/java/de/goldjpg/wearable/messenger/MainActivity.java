package de.goldjpg.wearable.messenger;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.wearable.intent.RemoteIntent;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

import androidx.wear.activity.ConfirmationActivity;
import de.goldjpg.wearable.messenger.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    View numberpane;
    View authpane;
    View loadingspinner;
    View acceptpane;
    EditText numberInput;
    EditText authcodeinput;
    static TGAPI client = null;
    TdApi.AuthorizationState authorizationState = null;
    UpdateHandler myhandler = new UpdateHandler();
    static SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        de.goldjpg.wearable.messenger.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if(client != null){
            Intent i = new Intent(this, Chat_Activity.class);
            startActivity(i);
            finish();
        }
        prefs = getSharedPreferences("config", MODE_PRIVATE);
        Settings.loadsettings(prefs);
        numberpane = findViewById(R.id.numberInputPane);
        authpane = findViewById(R.id.authInputPane);
        loadingspinner = findViewById(R.id.loadingSpinner);
        numberInput = findViewById(R.id.numberEdit);
        authcodeinput = findViewById(R.id.authEdit);
        acceptpane = findViewById(R.id.welcomePane);
        Button numberbut = findViewById(R.id.NumberSubmit);
        numberbut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String number = numberInput.getText().toString();
                if(number.startsWith("+")){
                    loadingspinner.setVisibility(View.VISIBLE);
                    numberpane.setVisibility(View.GONE);
                    client.client.send(new TdApi.SetAuthenticationPhoneNumber(numberInput.getText().toString(), new TdApi.PhoneNumberAuthenticationSettings(false, true, false)),new AuthorizationRequestHandler());
                }
            }
        });
        Button authbut = findViewById(R.id.authSubmit);
        authbut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = authcodeinput.getText().toString();
                if (!code.equals("")) {
                    TdApi.CheckAuthenticationCode authCode = new TdApi.CheckAuthenticationCode(code);
                    client.client.send(authCode,new AuthorizationRequestHandler());
                    loadingspinner.setVisibility(View.VISIBLE);
                    authpane.setVisibility(View.GONE);
                }
            }
        });
        Button acceptbutton = findViewById(R.id.acceptbutton);
        acceptbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.accepted = true;
                acceptpane.setVisibility(View.GONE);
                initClient();
            }
        });
        Button readmebutton = findViewById(R.id.readmebutton);
        readmebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .setData(Uri.parse("https://github.com/goldjpg/Wearable-Messenger"));

                RemoteIntent.startRemoteActivity(MainActivity.this, intent, null);
                Intent intent2 = new Intent(MainActivity.this, ConfirmationActivity.class);
                intent2.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.OPEN_ON_PHONE_ANIMATION);
                startActivity(intent2);
            }
        });
        if(Settings.accepted){
            initClient();
        }else{
            acceptpane.setVisibility(View.VISIBLE);
        }
    }

    private void initClient(){
        loadingspinner.setVisibility(View.VISIBLE);
        client = new TGAPI();
        client.mylistener.add(myhandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.mylistener.remove(myhandler);
        Settings.savesettings(prefs);
    }

    void print(String s){
        Log.i("Messenger", s);
    }

    private void onAuthorizationStateUpdated(TdApi.AuthorizationState nauthorizationState) {
        if (nauthorizationState != null) {
            authorizationState = nauthorizationState;
        }
        switch (authorizationState.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                print("authenticationg...");
                TdApi.TdlibParameters authStateRequest = new TdApi.TdlibParameters();
                authStateRequest.apiId = 16118529;
                authStateRequest.apiHash = "b373e5d2af03f688b1e38035580e66d7";
                authStateRequest.useMessageDatabase = true;
                authStateRequest.useSecretChats = false;
                authStateRequest.systemLanguageCode = "en";
                authStateRequest.databaseDirectory = getApplicationContext().getFilesDir().getAbsolutePath();
                authStateRequest.deviceModel = Build.PRODUCT;
                authStateRequest.applicationVersion = BuildConfig.VERSION_NAME;
                authStateRequest.systemVersion = Build.VERSION.RELEASE;
                authStateRequest.enableStorageOptimizer = true;
                client.client.send(new TdApi.SetTdlibParameters(authStateRequest), new AuthorizationRequestHandler());
                break;
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
                client.client.send(new TdApi.CheckDatabaseEncryptionKey(), new AuthorizationRequestHandler());
                break;
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
                MainActivity.this.runOnUiThread(() -> {
                    loadingspinner.setVisibility(View.GONE);
                    numberpane.setVisibility(View.VISIBLE);
                });
                break;
            case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR: {
                print("Waiting for Link auth...");
                //String link = ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) Example.authorizationState).link;
                //System.out.println("Please confirm this login link on another device: " + link);
                break;
            }
            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
                MainActivity.this.runOnUiThread(() -> {
                    loadingspinner.setVisibility(View.GONE);
                    authpane.setVisibility(View.VISIBLE);
                });
                break;
            case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR: {
                print("Waiting for Register....");
                //client.send(new TdApi.RegisterUser(firstName, lastName), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
                print("Waiting for Password....");
                //client.send(new TdApi.CheckAuthenticationPassword(password), new AuthorizationRequestHandler());
                break;
            }
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                MainActivity.this.runOnUiThread(() -> {
                    loadingspinner.setVisibility(View.GONE);
                    Intent i = new Intent(MainActivity.this, Chat_Activity.class);
                    startActivity(i);
                    finish();
                    Toast.makeText(MainActivity.this, "Logged in.", Toast.LENGTH_LONG).show();
                });
                break;
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
                print("Closed");
                client = new TGAPI();
                break;
            default:
                System.err.println("Unsupported authorization state:" + authorizationState);
        }
    }

    private class UpdateHandler implements TGAPI.ActionListener {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.UpdateAuthorizationState.CONSTRUCTOR:
                    onAuthorizationStateUpdated(((TdApi.UpdateAuthorizationState) object).authorizationState);
                    break;
            }
        }
    }

    private class AuthorizationRequestHandler implements Client.ResultHandler {
        @Override
        public void onResult(TdApi.Object object) {
            switch (object.getConstructor()) {
                case TdApi.Error.CONSTRUCTOR:
                    System.err.println("Receive an error:"  + object);
                    onAuthorizationStateUpdated(null); // repeat last action
                    break;
                case TdApi.Ok.CONSTRUCTOR:
                    break;
                default:
                    System.err.println("Receive wrong response from TDLib:"  + object);
            }
        }
    }

}