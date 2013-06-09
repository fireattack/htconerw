package me.timos.htconerw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ReceiverBoot extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences preferences = context.getSharedPreferences(
				Constant.KEY_SETTING_STORE, Context.MODE_PRIVATE);
		if (preferences.getBoolean(Constant.KEY_LOAD_ON_BOOT, false)) {
			Intent i = new Intent(context, ServiceLoadModule.class);
			context.startService(i);
		}
	}

}
