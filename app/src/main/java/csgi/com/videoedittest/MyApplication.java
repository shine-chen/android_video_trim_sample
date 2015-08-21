package csgi.com.videoedittest;

import android.app.Application;
import android.util.Log;

/**
 * Created by Shine on 8/12/15.
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler());
    }
}
