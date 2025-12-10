package ch.nadlo.oss.capacitor.pdf_viewer;

import android.app.Activity;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.getcapacitor.Bridge;
import com.getcapacitor.PluginCall;

public class PDFViewer {

    private static final String FRAGMENT_TAG = "PdfViewerFragmentTag";
    private static final String LOG_TAG = "PdfViewer.PDFViewer";

    private Bridge bridge;

    public void setBridge(Bridge bridge) {
        this.bridge = bridge;
    }

    public void openViewer(PluginCall call) {
        if (bridge == null) {
            Log.e(LOG_TAG, "openViewer: bridge is null");
            call.reject("Bridge not set");
            return;
        }

        final String url = call.getString("url", null);
        // Accept your existing JS API key "top"; fallback to "marginTop" if present.
        final int marginTopPx = call.getInt("top", call.getInt("marginTop", 0));

        Log.i(LOG_TAG, "openViewer: url=" + url + ", marginTopPx=" + marginTopPx);

        if (url == null || url.trim().isEmpty()) {
            Log.e(LOG_TAG, "openViewer: URL missing");
            call.reject("URL is required");
            return;
        }

        final Activity activity = bridge.getActivity();
        if (activity == null) {
            Log.e(LOG_TAG, "openViewer: activity is null");
            call.reject("No active activity");
            return;
        }

        activity.runOnUiThread(() -> {
            FragmentManager fm = ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager();

            // Remove any previous instance
            Fragment existing = fm.findFragmentByTag(FRAGMENT_TAG);
            if (existing != null) {
                Log.i(LOG_TAG, "openViewer: removing existing fragment");
                fm.beginTransaction().remove(existing).commitNowAllowingStateLoss();
            }

            Log.i(LOG_TAG, "openViewer: attaching PdfViewerFragment with marginTopPx=" + marginTopPx);
            PdfViewerFragment fragment = PdfViewerFragment.newInstance(url, marginTopPx);
            fm.beginTransaction()
              .add(android.R.id.content, fragment, FRAGMENT_TAG)
              .commitAllowingStateLoss();

            call.resolve();
        });
    }

    public void closeViewer(PluginCall call) {
        if (bridge == null) {
            Log.e(LOG_TAG, "closeViewer: bridge is null");
            call.reject("Bridge not set");
            return;
        }

        final Activity activity = bridge.getActivity();
        if (activity == null) {
            Log.e(LOG_TAG, "closeViewer: activity is null");
            call.reject("No active activity");
            return;
        }

        activity.runOnUiThread(() -> {
            FragmentManager fm = ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager();
            Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);
            if (fragment != null) {
                Log.i(LOG_TAG, "closeViewer: removing fragment");
                fm.beginTransaction().remove(fragment).commitAllowingStateLoss();
                fm.executePendingTransactions();
            } else {
                Log.i(LOG_TAG, "closeViewer: no fragment to remove");
            }
            call.resolve();
        });
    }
}
