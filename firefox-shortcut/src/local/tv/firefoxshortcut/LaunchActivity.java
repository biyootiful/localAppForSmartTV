package local.tv.firefoxshortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Home-screen tile that does one thing: open the sideloaded Firefox. */
public class LaunchActivity extends Activity {
    private static final String FIREFOX = "org.mozilla.firefox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent launch = getPackageManager().getLaunchIntentForPackage(FIREFOX);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Toast.makeText(this, "Firefox is not installed", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
