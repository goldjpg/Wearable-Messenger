package de.goldjpg.wearable.messenger;

import android.content.SharedPreferences;
import android.util.Log;

public class Settings {


    static boolean accepted = false;

    static boolean isLoaded = false;

    public static void loadsettings(SharedPreferences myprefs){
        if(isLoaded){
            return;
        }
        print("Loading settings...");
        accepted = myprefs.getBoolean("accepted", false);
        isLoaded = true;
    }

    public static void savesettings(SharedPreferences myprefs){
        if(!isLoaded){
            return;
        }
        myprefs.edit()
                .putBoolean("accepted", accepted)
                .apply();
        print("Settings saved.");
    }

    public static void print(String s){
        Log.i("Messenger", s);
    }

}
