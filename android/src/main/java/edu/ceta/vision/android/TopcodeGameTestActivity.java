package edu.ceta.vision.android;

import edu.ceta.vision.android.topcode.TopCodeDetectorAndroid;
import edu.ceta.vision.core.TopcodeGameTest;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class TopcodeGameTestActivity extends AndroidApplication {

	@Override
	public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
			initialize(new TopcodeGameTest(), config);
			boolean use_native_scanner = false;
			TopCodeDetectorAndroid detector = new TopCodeDetectorAndroid(40, false, 70, 5, true, false, use_native_scanner);
	}
}
