package ch.nadlo.oss.capacitor.pdf_viewer;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.util.FitPolicy;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deterministic top offset:
 *   guideBegin = top(px from JS) + 4dp safety (if top>0) + (Android 13+ ? 20dp : 0).
 * Root above guideline is transparent; PDF area is white.
 * Bottom padding uses system navigation/gesture insets so last page isn't obscured.
 * Scroll handle/page-number bubble is disabled.
 */
public class PdfViewerFragment extends Fragment {

    private static final String ARG_URL = "url";
    private static final String ARG_MARGIN_TOP = "marginTop"; // pixels from JS
    private static final String LOG_TAG = "PdfViewer.Fragment";
    private static final float ANDROID_13_EXTRA_TOP_DP = 20f; // requested fudge for Android 13+

    private String pdfUrl;
    private int marginTopPx;    // raw from JS
    private int effectiveTopPx; // top + 4dp safety + (SDK>=33 ? 20dp : 0)

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ConstraintLayout root;
    private Guideline guidelineTop;
    private PDFView pdfView;
    private ProgressBar progress;

    public static PdfViewerFragment newInstance(String url, int marginTopPx) {
        PdfViewerFragment fragment = new PdfViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_MARGIN_TOP, marginTopPx);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = null;
        int mt = 0;
        if (getArguments() != null) {
            url = getArguments().getString(ARG_URL);
            mt  = getArguments().getInt(ARG_MARGIN_TOP, 0);
        }
        pdfUrl = url;
        marginTopPx = Math.max(0, mt);

        float density = requireContext().getResources().getDisplayMetrics().density;
        int safetyPx = marginTopPx > 0 ? Math.max(1, Math.round(4f * density)) : 0;
        int osFudgePx = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Math.round(ANDROID_13_EXTRA_TOP_DP * density)
                : 0;

        effectiveTopPx = marginTopPx + safetyPx + osFudgePx;

        setRetainInstance(false);

        Log.i(LOG_TAG, "onCreate: url=" + pdfUrl
                + ", top(raw)=" + marginTopPx
                + ", safetyPx(4dp)=" + safetyPx
                + ", osFudgePx(Android13+=" + osFudgePx + ")"
                + ", guideBegin=" + effectiveTopPx);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context ctx = requireContext();

        // Root (transparent so your web header shows above the guideline)
        root = new ConstraintLayout(ctx);
        root.setId(View.generateViewId());
        root.setLayoutParams(new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.TRANSPARENT);

        // Guideline at EXACT effectiveTopPx
        guidelineTop = new Guideline(ctx);
        guidelineTop.setId(View.generateViewId());
        ConstraintLayout.LayoutParams glp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        glp.orientation = ConstraintLayout.LayoutParams.HORIZONTAL;
        glp.guideBegin = effectiveTopPx;
        guidelineTop.setLayoutParams(glp);
        root.addView(guidelineTop);

        // Progress spinner
        progress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleLarge);
        progress.setId(View.generateViewId());
        root.addView(progress);
        ConstraintLayout.LayoutParams plp = (ConstraintLayout.LayoutParams) progress.getLayoutParams();
        plp.width  = ConstraintLayout.LayoutParams.WRAP_CONTENT;
        plp.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
        plp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        plp.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID;
        plp.topToTop     = ConstraintLayout.LayoutParams.PARENT_ID;
        plp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        progress.setLayoutParams(plp);

        // PDFView (opaque white area below the guideline)
        pdfView = new PDFView(ctx, null);
        pdfView.setId(View.generateViewId());
        pdfView.setBackgroundColor(Color.WHITE);
        root.addView(pdfView);
        ConstraintLayout.LayoutParams pdfLp = (ConstraintLayout.LayoutParams) pdfView.getLayoutParams();
        pdfLp.width  = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
        pdfLp.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
        pdfLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        pdfLp.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID;
        pdfLp.topToBottom  = guidelineTop.getId();
        pdfLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        pdfView.setLayoutParams(pdfLp);

        // Apply ONLY bottom system inset as padding; do not modify top
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            WindowInsetsCompat wic = insets;
            Insets sysBars = wic.getInsets(WindowInsetsCompat.Type.systemBars());
            int navBottom = sysBars.bottom;
            pdfView.setPadding(0, 0, 0, navBottom);
            pdfView.setClipToPadding(false);
            Log.i(LOG_TAG, "insets: bottom=" + navBottom + " (applied as PDFView padding)");
            return insets;
        });

        // Layout log
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Log.i(LOG_TAG, "layout: guideBegin=" +
                        ((ConstraintLayout.LayoutParams) guidelineTop.getLayoutParams()).guideBegin +
                        ", pdfView.top=" + pdfView.getTop() + ", h=" + pdfView.getHeight());
            }
        });

        progress.setVisibility(View.VISIBLE);
        loadPdfSmart();

        return root;
    }

    private void loadPdfSmart() {
        if (TextUtils.isEmpty(pdfUrl)) {
            Log.e(LOG_TAG, "Empty PDF URL");
            progress.setVisibility(View.GONE);
            return;
        }

        if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
            io.execute(() -> {
                HttpURLConnection conn = null;
                File out = null;
                try {
                    URL url = new URL(pdfUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.connect();
                    int code = conn.getResponseCode();
                    Log.i(LOG_TAG, "download HTTP code=" + code);
                    if (code >= 200 && code < 300) {
                        InputStream in = new BufferedInputStream(conn.getInputStream());
                        out = File.createTempFile("triapp_pdf_", ".pdf", requireContext().getCacheDir());
                        FileOutputStream fos = new FileOutputStream(out);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                        fos.flush();
                        fos.close();
                        in.close();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Download failed", e);
                } finally {
                    if (conn != null) conn.disconnect();
                }

                if (!isAdded()) return;
                File finalOut = out;
                requireActivity().runOnUiThread(() -> {
                    if (finalOut != null && finalOut.exists()) {
                        loadFromFile(finalOut);
                    } else {
                        progress.setVisibility(View.GONE);
                    }
                });
            });
            return;
        }

        if (pdfUrl.startsWith("content://") || pdfUrl.startsWith("file://")) {
            try {
                loadFromUri(Uri.parse(pdfUrl));
            } catch (Exception e) {
                Log.e(LOG_TAG, "Invalid URI: " + pdfUrl, e);
                progress.setVisibility(View.GONE);
            }
            return;
        }

        File f = new File(pdfUrl);
        if (f.exists()) {
            loadFromFile(f);
        } else {
            Log.e(LOG_TAG, "File not found: " + pdfUrl);
            progress.setVisibility(View.GONE);
        }
    }

    private PDFView.Configurator applyCommonConfig(PDFView.Configurator c) {
        // NOTE: no scroll handle added (removes draggable page-number bubble)
        c.defaultPage(0)
         .enableSwipe(true)
         .swipeHorizontal(false)
         .enableDoubletap(true)
         .enableAnnotationRendering(true)
         .autoSpacing(true)
         .pageFling(true)
         .pageSnap(true)
         .spacing(10)                 // dp between pages
         .pageFitPolicy(FitPolicy.WIDTH)
         .enableAntialiasing(true)
         .onLoad(nbPages -> {
             Log.i(LOG_TAG, "PDF onLoad: pages=" + nbPages);
             progress.setVisibility(View.GONE);
         })
         .onError(t -> {
             Log.e(LOG_TAG, "PDF onError", t);
             progress.setVisibility(View.GONE);
         });
        return c;
    }

    private void loadFromFile(File file) {
        try {
            Log.i(LOG_TAG, "loadFromFile: " + file.getAbsolutePath());
            applyCommonConfig(pdfView.fromFile(file)).load();
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Load from file failed", t);
            progress.setVisibility(View.GONE);
        }
    }

    private void loadFromUri(Uri uri) {
        try {
            Log.i(LOG_TAG, "loadFromUri: " + uri);
            applyCommonConfig(pdfView.fromUri(uri)).load();
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Load from uri failed", t);
            progress.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        try {
            if (pdfView != null) {
                Log.i(LOG_TAG, "onDestroyView: recycling pdfView");
                pdfView.recycle();
            }
        } catch (Throwable ignored) {}
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
