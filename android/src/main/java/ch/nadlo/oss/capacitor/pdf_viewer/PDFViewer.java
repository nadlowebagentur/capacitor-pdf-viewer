package ch.nadlo.oss.capacitor.pdf_viewer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.webkit.WebView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class PDFViewer {

    private static final String FRAGMENT_TAG = "PdfViewerFragmentTag";
    private static final String LOG_TAG = "PdfViewer.PDFViewer";
    private static final String MODE_TAG  = "PdfViewer.Mode";

    private Bridge bridge;
    private PdfViewerFragment activeFragment;
    private Drawable savedWebViewContainerBackground = null;

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
            activeFragment = fragment;

            fm.beginTransaction()
              .add(android.R.id.content, fragment, FRAGMENT_TAG)
              .commitAllowingStateLoss();

            // Flush synchronously so activeFragment.getView() is non-null
            // before any subsequent setMode() call arrives on the main thread.
            fm.executePendingTransactions();

            android.view.ViewGroup content = activity.findViewById(android.R.id.content);
            Log.i(MODE_TAG, "openViewer: committed, content child count=" + content.getChildCount()
                    + ", fragment view=" + (activeFragment.getView() != null ? "ready" : "null"));

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
                activeFragment = null;
                Log.i(MODE_TAG, "closeViewer: done, activeFragment=null");
            } else {
                Log.i(LOG_TAG, "closeViewer: no fragment to remove");
                Log.i(MODE_TAG, "closeViewer: fragment not found by tag (activeFragment=" + activeFragment + ")");
            }
            call.resolve();
        });
    }

    public void setMode(PluginCall call) {
        if (bridge == null) {
            call.reject("Bridge not set");
            return;
        }

        final String mode = call.getString("mode", "front");
        final Activity activity = bridge.getActivity();
        if (activity == null) {
            call.reject("No active activity");
            return;
        }

        activity.runOnUiThread(() -> {
            Log.i(MODE_TAG, "setMode(" + mode + "): activeFragment=" + activeFragment);

            android.view.View fragmentView = activeFragment != null ? activeFragment.getView() : null;
            if (fragmentView == null) {
                Log.w(MODE_TAG, "setMode(" + mode + "): fragmentView is null – skipping reorder");
                call.resolve();
                return;
            }

            android.view.ViewGroup parent = (android.view.ViewGroup) fragmentView.getParent();
            if (parent == null) {
                Log.w(MODE_TAG, "setMode(" + mode + "): fragmentView has no parent – skipping reorder");
                call.resolve();
                return;
            }

            int childCount = parent.getChildCount();
            int currentIndex = -1;
            for (int i = 0; i < childCount; i++) {
                if (parent.getChildAt(i) == fragmentView) { currentIndex = i; break; }
            }
            Log.i(MODE_TAG, "setMode(" + mode + "): parent=" + parent.getClass().getSimpleName()
                    + " childCount=" + childCount + " fragmentIndex=" + currentIndex);

            // Log all sibling views for hierarchy visibility
            for (int i = 0; i < childCount; i++) {
                android.view.View child = parent.getChildAt(i);
                Log.i(MODE_TAG, "  child[" + i + "] " + child.getClass().getSimpleName()
                        + " id=" + child.getId() + " z=" + child.getZ());
            }

            parent.removeView(fragmentView);
            if ("back".equals(mode)) {
                parent.addView(fragmentView, 0); // index 0 = behind WebView container
                Log.i(MODE_TAG, "setMode(back): moved fragment to index 0 (behind webview)");

                // Make the WebView container transparent so PDF is visible through it
                android.view.View webViewContainer = bridge.getWebView() != null
                        ? (android.view.View) bridge.getWebView().getParent()
                        : null;
                if (webViewContainer != null) {
                    savedWebViewContainerBackground = webViewContainer.getBackground();
                    webViewContainer.setBackgroundColor(Color.TRANSPARENT);
                    Log.i(MODE_TAG, "setMode(back): webViewContainer background set to transparent");
                }
                WebView wv = bridge.getWebView();
                if (wv != null) {
                    wv.setBackgroundColor(Color.TRANSPARENT);
                    Log.i(MODE_TAG, "setMode(back): webView background set to transparent");
                }
            } else {
                parent.addView(fragmentView);    // last index = in front
                Log.i(MODE_TAG, "setMode(front): moved fragment to index " + (parent.getChildCount() - 1));

                // Restore WebView container background
                android.view.View webViewContainer = bridge.getWebView() != null
                        ? (android.view.View) bridge.getWebView().getParent()
                        : null;
                if (webViewContainer != null) {
                    if (savedWebViewContainerBackground != null) {
                        webViewContainer.setBackground(savedWebViewContainerBackground);
                        savedWebViewContainerBackground = null;
                    } else {
                        webViewContainer.setBackgroundColor(Color.WHITE);
                    }
                    Log.i(MODE_TAG, "setMode(front): webViewContainer background restored");
                }
                WebView wv = bridge.getWebView();
                if (wv != null) {
                    wv.setBackgroundColor(Color.WHITE);
                    Log.i(MODE_TAG, "setMode(front): webView background restored to white");
                }
            }

            call.resolve();
        });
    }

    public void getViewerStatus(PluginCall call) {
        if (bridge == null) {
            Log.e(LOG_TAG, "getViewerStatus: bridge is null");
            call.reject("Bridge not set");
            return;
        }

        final Activity activity = bridge.getActivity();
        if (activity == null) {
            Log.e(LOG_TAG, "getViewerStatus: activity is null");
            call.reject("No active activity");
            return;
        }

        activity.runOnUiThread(() -> {
            FragmentManager fm = ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager();
            Fragment fragment = fm.findFragmentByTag(FRAGMENT_TAG);

            PdfViewerFragment viewer = fragment instanceof PdfViewerFragment
                    ? (PdfViewerFragment) fragment
                    : activeFragment;

            PdfViewerFragment.ViewerStatus status = viewer != null
                    ? viewer.snapshotStatus()
                    : PdfViewerFragment.ViewerStatus.closed();

            JSObject result = new JSObject();
            result.put("isOpen", status.isOpen);
            result.put("isAtEnd", status.isAtEnd);
            result.put("page", status.currentPage);
            result.put("pageCount", status.pageCount);
            call.resolve(result);
        });
    }
}
