package ch.nadlo.oss.capacitor.pdf_viewer;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PDFViewer")
public class PDFViewerPlugin extends Plugin {

    private PDFViewer implementation;

    @Override
    public void load() {
        super.load();
        implementation = new PDFViewer();
        implementation.setBridge(this.getBridge());
    }

    @PluginMethod
    public void open(PluginCall call) {
        implementation.openViewer(call);
    }

    @PluginMethod
    public void close(PluginCall call) {
        implementation.closeViewer(call);
    }
}
