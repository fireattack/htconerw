package me.timos.htconerw;

import android.util.Log;

public class Logcat {

	public static final void d(Object caller, String message) {
		Log.d(Constant.TAG, caller.getClass().getSimpleName() + ": " + message);
	}

	public static final void d(String message) {
		Log.d(Constant.TAG, message);
	}

	public static final void e(Object caller, String message) {
		Log.e(Constant.TAG, caller.getClass().getSimpleName() + ": " + message);
	}

	public static final void e(Object caller, String message, Throwable tr) {
		Log.e(Constant.TAG, caller.getClass().getSimpleName() + ": " + message,
				tr);
	}

	public static final void e(String message) {
		Log.e(Constant.TAG, message);
	}

	public static final void e(String message, Throwable tr) {
		Log.e(Constant.TAG, message, tr);
	}

}
