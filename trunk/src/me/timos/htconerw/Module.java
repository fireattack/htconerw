package me.timos.htconerw;

public enum Module {
	MOD41(R.raw.wp_mod_ko_41, 334, 15),
	//
	MOD43(R.raw.wp_mod_ko_43, 341, 15);

	public final int RES_ID;
	public final int OFFSET;
	public final int LENGTH;

	private Module(int resId, int offset, int length) {
		RES_ID = resId;
		OFFSET = offset;
		LENGTH = length;
	}
}
