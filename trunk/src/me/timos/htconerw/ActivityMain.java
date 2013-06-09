package me.timos.htconerw;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class ActivityMain extends Activity implements OnCheckedChangeListener,
		OnClickListener {

	private CheckBox mCheckBoxLoadOnBoot;
	private Button mButtonLoad;

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		Editor editor = getSharedPreferences(Constant.KEY_SETTING_STORE,
				MODE_PRIVATE).edit();
		editor.putBoolean(Constant.KEY_LOAD_ON_BOOT, isChecked);
		editor.apply();
	}

	@Override
	public void onClick(View v) {
		if (v == mButtonLoad) {
			Intent i = new Intent(this, ServiceLoadModule.class);
			startService(i);
		}
		finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mCheckBoxLoadOnBoot = (CheckBox) findViewById(R.id.loadOnBoot);
		mButtonLoad = (Button) findViewById(R.id.load);
		Button buttonExit = (Button) findViewById(R.id.exit);
		TextView textInfo = (TextView) findViewById(R.id.info);
		textInfo.setText(Html.fromHtml(getString(R.string.info)));

		if (savedInstanceState == null) {
			mCheckBoxLoadOnBoot.setChecked(getSharedPreferences(
					Constant.KEY_SETTING_STORE, MODE_PRIVATE).getBoolean(
					Constant.KEY_LOAD_ON_BOOT, false));
		}

		mCheckBoxLoadOnBoot.setOnCheckedChangeListener(this);
		mButtonLoad.setOnClickListener(this);
		buttonExit.setOnClickListener(this);
	}

}
