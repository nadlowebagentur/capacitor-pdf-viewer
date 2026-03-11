package ch.nadlo.oss.capacitor.pdf_viewer;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.webkit.WebView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class PDFViewer {

    private static final String FRAGMENT_TAG = "PdfViewerFragmentTag";
    private static final String LOG_TAG = "PdfViewer.PDFViewer";

    private Bridge bridge;
    private PdfViewerFragment activeFragment;

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

            // Add the fragment inside WebView's parent (same container as WebView),
            // mirroring VSPlayer: PDF at index 0 (bottom), WebView at index 1 (transparent on top).
            WebView webView = bridge.getWebView();
            android.view.ViewGroup webViewParent = (android.view.ViewGroup) webView.getParent();
            if (webViewParent.getId() == android.view.View.NO_ID) {
                webViewParent.setId(android.view.View.generateViewId());
            }
            int pdfContainerId = webViewParent.getId();

            Log.i(LOG_TAG, "openViewer: attaching PdfViewerFragment with marginTopPx=" + marginTopPx
                    + " into container id=" + pdfContainerId
                    + " (" + webViewParent.getClass().getSimpleName() + ")");

            PdfViewerFragment fragment = PdfViewerFragment.newInstance(url, marginTopPx);
            activeFragment = fragment;

            fm.beginTransaction()
              .add(pdfContainerId, fragment, FRAGMENT_TAG)
              .commitAllowingStateLoss();

            // Flush synchronously so activeFragment.getView() is non-null
            // before any subsequent setMode() call arrives on the main thread.
            fm.executePendingTransactions();

            // Force CoordinatorLayout.LayoutParams (MATCH_PARENT) — plain ViewGroup.LayoutParams
            // causes a ClassCastException when CoordinatorLayout measures its children.
            if (activeFragment.getView() != null) {
                activeFragment.getView().setLayoutParams(new CoordinatorLayout.LayoutParams(
                        CoordinatorLayout.LayoutParams.MATCH_PARENT,
                        CoordinatorLayout.LayoutParams.MATCH_PARENT));
            }

            // Make WebView transparent so PDF at index 0 shows through (same as VSPlayer)
            webView.setBackgroundColor(Color.TRANSPARENT);

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
            } else {
                Log.i(LOG_TAG, "closeViewer: no fragment to remove");
            }
            // Restore WebView opaque background
            WebView webView = bridge.getWebView();
            if (webView != null) {
                webView.setBackgroundColor(Color.WHITE);
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
            android.view.View fragmentView = activeFragment != null ? activeFragment.getView() : null;
            if (fragmentView == null) {
                Log.w(LOG_TAG, "setMode(" + mode + "): fragmentView is null – skipping reorder");
                call.resolve();
                return;
            }

            android.view.ViewGroup parent = (android.view.ViewGroup) fragmentView.getParent();
            if (parent == null) {
                Log.w(LOG_TAG, "setMode(" + mode + "): fragmentView has no parent – skipping reorder");
                call.resolve();
                return;
            }

            // Use bringToFront() instead of removeView/addView — bringToFront uses internal
            // array manipulation (no onDetachedFromWindow / onAttachedToWindow callbacks),
            // which prevents the PDF view from losing its rendered content.
            if ("back".equals(mode)) {
                // Bring WebView to front → PDF fragment implicitly moves behind it
                bridge.getWebView().bringToFront();
            } else {
                // Bring PDF fragment to front → in front of WebView
                fragmentView.bringToFront();
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
