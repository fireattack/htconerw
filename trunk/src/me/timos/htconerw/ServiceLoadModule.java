package me.timos.htconerw;

import static me.timos.htconerw.Module.MOD41;
import static me.timos.htconerw.Module.MOD43;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;

public class ServiceLoadModule extends IntentService {

	private int mOffset;
	private int mResId;
	private int mLength;
	private Handler mHandler;
	private File mFilesDir;
	private Toast mToast;

	public ServiceLoadModule() {
		super("ServiceLoadModule");
	}

	private String exec(String workingDir, Integer timeout, String... commands) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			Logcat.e("Warning execute commands on main thread\n"
					+ Arrays.toString(commands));
		}
		try {
			if (workingDir != null) {
				CommandCapture cc = new CommandCapture(0, "cd " + workingDir);
				RootTools.getShell(true).add(cc);
			}

			final StringBuilder sb = new StringBuilder(4096);

			Command c = new Command(0, commands) {
				@Override
				public void output(int id, String line) {
					sb.append("\n").append(line);
				}
			};

			if (timeout == null) {
				RootTools.getShell(true).add(c).waitForFinish();
			} else {
				RootTools.getShell(true).add(c).waitForFinish(timeout);
			}
			return sb.length() > 0 ? sb.substring(1) : "";
		} catch (Exception e) {
			Logcat.e("Error execute command\n" + Arrays.toString(commands), e);
			return "";
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mHandler = new Handler(getMainLooper());
		mFilesDir = getFilesDir();

		if (Build.VERSION.SDK_INT < 18) {
			mResId = MOD41.RES_ID;
			mOffset = MOD41.OFFSET;
			mLength = MOD41.LENGTH;
		} else {
			mResId = MOD43.RES_ID;
			mOffset = MOD43.OFFSET;
			mLength = MOD43.LENGTH;
		}

		// Check device
		if (!Build.DEVICE.equalsIgnoreCase("m7")
				&& !Build.DEVICE.equalsIgnoreCase("m7wls")) {
			showToast(R.string.message_device_unsupported, Toast.LENGTH_LONG);
			return;
		}

		// Prepare busybox
		File busybox = new File(mFilesDir, "busybox");
		if (!busybox.exists()) {
			if (!writeFileFromRes(R.raw.busybox, busybox)) {
				showToast(R.string.message_error, Toast.LENGTH_LONG);
				Logcat.e("ERROR: Can't write busybox");
				return;
			}
			busybox.setExecutable(true);
		}

		// check verMargic compared to previous run
		SharedPreferences preferences = getSharedPreferences(
				Constant.KEY_SETTING_STORE, MODE_PRIVATE);
		Editor editor = preferences.edit();
		String currVerMagic = exec(mFilesDir.toString(), null,
				"./busybox uname -r").trim();
		String savedVerMagic = preferences
				.getString(Constant.KEY_VER_MAGIC, "");
		if (currVerMagic.isEmpty()) {
			showToast(R.string.message_error_root, Toast.LENGTH_LONG);
			Logcat.e("ERROR: no root");
			return;
		}
		if (!savedVerMagic.isEmpty() && !savedVerMagic.equals(currVerMagic)) {
			showToast(R.string.message_kernel_change, Toast.LENGTH_LONG);
			editor.remove(Constant.KEY_LOAD_ON_BOOT);
			editor.remove(Constant.KEY_VER_MAGIC);
			editor.apply();
			return;
		}

		// Prepare module
		if (!prepModule(currVerMagic.getBytes())) {
			showToast(R.string.message_error, Toast.LENGTH_LONG);
			Logcat.e("ERROR: Error preping module");
			return;
		}

		// Load module
		String result = exec(mFilesDir.toString(), null,
				"./busybox insmod wp_mod.ko", "./busybox lsmod");
		Logcat.d(result);
		if (result.contains("wp_mod ")) {
			showToast(R.string.message_module_load_ok, Toast.LENGTH_LONG);
			editor.putString(Constant.KEY_VER_MAGIC, currVerMagic);
			editor.apply();
		} else {
			Logcat.e("ERROR: Couldn't load module");
			showToast(R.string.message_error, Toast.LENGTH_LONG);
		}
	}

	private boolean prepModule(byte[] verMagic) {
		File wpMod = new File(mFilesDir, "wp_mod.ko");

		// Check existing module
		RandomAccessFile raf = null;
		try {
			try {
				raf = new RandomAccessFile(wpMod, "r");
				byte[] buffer = new byte[verMagic.length];
				raf.seek(mOffset);
				raf.read(buffer, 0, verMagic.length);
				if (Arrays.equals(verMagic, buffer)) {
					return true;
				}
			} finally {
				raf.close();
			}
		} catch (Exception e) {
		}

		// Modify if required and write from raw
		InputStream in = null;
		OutputStream out = null;
		ByteArrayOutputStream baos = null;
		try {
			try {
				in = getResources().openRawResource(mResId);
				out = new FileOutputStream(wpMod);
				byte[] buff = new byte[4096];
				baos = new ByteArrayOutputStream(in.available());
				int count;
				while ((count = in.read(buff)) != -1) {
					baos.write(buff, 0, count);
				}
				byte[] modRawBytes = baos.toByteArray();
				byte[] modVerMagic = Arrays.copyOfRange(modRawBytes, mOffset,
						mOffset + mLength);
				Logcat.d("CURRVERMAGIC " + new String(verMagic));
				Logcat.d("MODVERMAGIC " + new String(modVerMagic));
				if (Arrays.equals(verMagic, modVerMagic)) {
					// If raw module has the same verMagic write it out
					out.write(modRawBytes);
				} else {
					Logcat.d("CURRLENGTH " + verMagic.length);
					if (mLength == verMagic.length) {
						// Same length -> Modify and write out
						System.arraycopy(verMagic, 0, modRawBytes, mOffset,
								verMagic.length);
						out.write(modRawBytes);
					} else {
						// TODO: Should we do this? User is likely running
						// custom kernel?
						byte[] newModRawByte = new byte[modRawBytes.length
								+ verMagic.length - mLength];
						System.arraycopy(modRawBytes, 0, newModRawByte, 0,
								mOffset);
						System.arraycopy(verMagic, 0, newModRawByte, mOffset,
								verMagic.length);
						System.arraycopy(modRawBytes, mOffset + mLength,
								newModRawByte, mOffset + verMagic.length,
								modRawBytes.length - mOffset - mLength);
						out.write(newModRawByte);
					}
				}
				return true;
			} finally {
				in.close();
				out.close();
				baos.close();
			}
		} catch (Exception e) {
			Logcat.e("ERROR: Couldn't write module file");
			return false;
		}
	}

	private void showToast(int resId, int duration) {
		try {
			showToast(getString(resId), duration);
		} catch (Exception e) {
		}
	}

	private void showToast(final String toast, final int duration) {
		mHandler.post(new Runnable() {
			public void run() {
				if (mToast == null) {
					mToast = Toast.makeText(ServiceLoadModule.this, toast,
							duration);
				} else {
					mToast.setText(toast);
					mToast.setDuration(duration);
				}
				mToast.show();
			}
		});
	}

	private boolean writeFileFromRes(int srcRawId, File newFile) {
		InputStream in = null;
		OutputStream out = null;
		try {
			try {
				in = getResources().openRawResource(srcRawId);
				out = new FileOutputStream(newFile);

				byte[] buffer = new byte[4096];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			} finally {
				in.close();
				out.close();
			}
			return true;
		} catch (Exception e) {
			Logcat.e("Error writing file " + newFile.toString(), e);
			return false;
		}
	}

}
