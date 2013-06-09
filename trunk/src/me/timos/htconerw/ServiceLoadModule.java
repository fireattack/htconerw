package me.timos.htconerw;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

import android.app.IntentService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;

public class ServiceLoadModule extends IntentService {

	private static final int MOD_OFFSET = 334;
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
		if (!Build.DEVICE.equalsIgnoreCase("m7")) {
			showToast("Not a HTC One M7U or M7UL?", Toast.LENGTH_LONG);
			return;
		}

		mHandler = new Handler(getMainLooper());
		mFilesDir = getFilesDir();

		// Prepare busybox
		File busybox = new File(mFilesDir, "busybox");
		if (!busybox.exists()) {
			if (!writeFileFromRes(R.raw.busybox, busybox)) {
				showToast(R.string.message_error, Toast.LENGTH_LONG);
				return;
			}
			busybox.setExecutable(true);
		}

		// Prepare module
		byte[] currKernVerMagic = exec(mFilesDir.toString(), null,
				"./busybox uname -r").trim().getBytes();
		if (!prepModule(currKernVerMagic)) {
			showToast(R.string.message_error, Toast.LENGTH_LONG);
			return;
		}

		// Load module
		String result = exec(mFilesDir.toString(), null,
				"./busybox insmod wp_mod.ko", "./busybox lsmod");
		if (result.contains("wp_mod ")) {
			Logcat.d(result);
			showToast(R.string.message_module_load_ok, Toast.LENGTH_LONG);
		} else {
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
				raf.seek(MOD_OFFSET);
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
		try {
			try {
				in = getResources().openRawResource(R.raw.wp_mod);
				out = new FileOutputStream(wpMod);
				byte[] modRawBytes = new byte[in.available()];
				in.read(modRawBytes);
				Logcat.d("CURRVERMAGIC " + new String(verMagic));
				Logcat.d("MODVERMAGIC "
						+ new String(Arrays.copyOfRange(modRawBytes,
								MOD_OFFSET, MOD_OFFSET + verMagic.length)));
				if (Arrays.equals(
						verMagic,
						Arrays.copyOfRange(modRawBytes, MOD_OFFSET, MOD_OFFSET
								+ verMagic.length))) {
					// If raw module has the same verMagic write it out
					out.write(modRawBytes);
				} else {
					// Get verMagic size
					int count = 0;
					for (int i = MOD_OFFSET; i < modRawBytes.length; i++) {
						if (modRawBytes[i] == ' ') {
							break;
						}
						count++;
					}
					Logcat.d("CURRLENGTH " + verMagic.length);
					Logcat.d("MODLENGTH " + count);

					if (count == verMagic.length) {
						System.arraycopy(verMagic, 0, modRawBytes, MOD_OFFSET,
								verMagic.length);
						out.write(modRawBytes);
					} else {
						// TODO: Should we do this? User is likely running
						// custom kernel?
						byte[] newModRawByte = new byte[modRawBytes.length
								+ count - verMagic.length];
						System.arraycopy(modRawBytes, 0, newModRawByte, 0,
								MOD_OFFSET);
						System.arraycopy(verMagic, 0, newModRawByte,
								MOD_OFFSET, verMagic.length);
						System.arraycopy(modRawBytes, MOD_OFFSET
								+ verMagic.length, newModRawByte, MOD_OFFSET
								+ verMagic.length, modRawBytes.length
								- MOD_OFFSET - verMagic.length);
						out.write(newModRawByte);
					}
				}
				return true;
			} finally {
				in.close();
				out.close();
			}
		} catch (Exception e) {
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
