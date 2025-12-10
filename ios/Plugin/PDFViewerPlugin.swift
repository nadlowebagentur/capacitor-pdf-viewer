import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(PDFViewerPlugin)
public class PDFViewerPlugin: CAPPlugin {
    private let implementation = PDFViewer()

    @objc func open(_ call: CAPPluginCall) {
        if let url = call.getString("url") {
            if let pdfURL = URL(string: url) {
                let top = call.getInt("top") ?? 0;
                
                implementation.open(pdfURL, top: top);
            }
        }
                
        call.resolve()
    }
    
    @objc func close(_ call: CAPPluginCall) {
        implementation.closeViewer();
        
        call.resolve()
    }
    
    @objc func getStatus(_ call: CAPPluginCall) {
        let status = implementation.getStatus()
        call.resolve(status)
    }
}
