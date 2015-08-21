package csgi.com.videoedittest;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by Shine on 1/18/15.
 */
public class MyExceptionHandler implements Thread.UncaughtExceptionHandler {

    public MyExceptionHandler() {
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        StringWriter stackTrace = new StringWriter();
        ex.printStackTrace(new PrintWriter(stackTrace));
        System.err.println(stackTrace);
        System.err.flush();

        android.os.Process.killProcess(android.os.Process.myPid());

        System.exit(0);
    }
}
