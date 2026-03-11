import Foundation
import UIKit
import PDFKit
import WebKit

@objc public class PDFViewer: NSObject {
    private let pdfView = PDFView()
    private var pageCount: Int = 0
    private var currentPage: Int = 0
    private var pageChangeObserver: NSObjectProtocol?
    
    @objc public func open(_ pdfURL: URL, top: Int = 0) {
        DispatchQueue.main.sync {
            if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
                if !rootViewController.view.contains(self.pdfView) {
                    rootViewController.view.addSubview(self.pdfView);
                }
            }
        }
        
        guard let document = PDFDocument(url: pdfURL) else {
            return
        }
        
        DispatchQueue.main.async {
            if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
                let rootView = rootViewController.view!
                self.pdfView.frame = rootView.bounds;
                self.pdfView.frame.origin.y = CGFloat(top);
                self.pdfView.frame.size.height = rootView.bounds.height - CGFloat(top)

                rootView.bringSubviewToFront(self.pdfView)

                print("[PdfViewer.Mode] open: pdfView added, rootView subviews (\(rootView.subviews.count)):")
                for (i, sv) in rootView.subviews.enumerated() {
                    let marker = sv === self.pdfView ? " ← pdfView" : ""
                    print("[PdfViewer.Mode]   [\(i)] \(type(of: sv)) isOpaque=\(sv.isOpaque)\(marker)")
                }

                // make PDF fit full width
                self.pdfView.autoScales = true
                self.pdfView.displayMode = .singlePageContinuous
                self.pdfView.displayDirection = .vertical
                self.pdfView.displaysAsBook = false

                self.pdfView.document = document
                self.pageCount = document.pageCount
                self.currentPage = 0
                self.registerPageChangeObserver()
            }
        }
    }
    
    @objc public func setMode(_ mode: String, webView: WKWebView?) {
        DispatchQueue.main.async {
            guard let rootViewController = UIApplication.shared.keyWindow?.rootViewController else {
                print("[PdfViewer.Mode] setMode(\(mode)): rootViewController is nil")
                return
            }
            let rootView = rootViewController.view!

            print("[PdfViewer.Mode] setMode(\(mode)) ─────────────────────────")
            print("[PdfViewer.Mode]   pdfView.superview: \(self.pdfView.superview.map { "\(type(of: $0))" } ?? "nil")")
            if let wv = webView {
                print("[PdfViewer.Mode]   webView.superview: \(wv.superview.map { "\(type(of: $0))" } ?? "nil")")
                print("[PdfViewer.Mode]   webView === rootView child: \(rootView.subviews.contains(wv))")
                print("[PdfViewer.Mode]   webView.isOpaque=\(wv.isOpaque) bg=\(String(describing: wv.backgroundColor))")
            } else {
                print("[PdfViewer.Mode]   webView: nil (not passed)")
            }
            print("[PdfViewer.Mode]   rootView direct subviews (\(rootView.subviews.count)):")
            for (i, sv) in rootView.subviews.enumerated() {
                let marker = sv === self.pdfView ? " ← pdfView" : (sv === webView ? " ← webView" : "")
                print("[PdfViewer.Mode]     [\(i)] \(type(of: sv)) isOpaque=\(sv.isOpaque) bg=\(String(describing: sv.backgroundColor))\(marker)")
            }

            if mode == "back" {
                rootView.sendSubviewToBack(self.pdfView)
                webView?.isOpaque = false
                webView?.backgroundColor = .clear
                webView?.scrollView.backgroundColor = .clear
                print("[PdfViewer.Mode]   → sent pdfView to back, webView made transparent")
            } else {
                rootView.bringSubviewToFront(self.pdfView)
                webView?.isOpaque = true
                webView?.backgroundColor = .white
                webView?.scrollView.backgroundColor = .white
                print("[PdfViewer.Mode]   → brought pdfView to front, webView restored opaque")
            }

            print("[PdfViewer.Mode]   rootView subviews AFTER (\(rootView.subviews.count)):")
            for (i, sv) in rootView.subviews.enumerated() {
                let marker = sv === self.pdfView ? " ← pdfView" : (sv === webView ? " ← webView" : "")
                print("[PdfViewer.Mode]     [\(i)] \(type(of: sv))\(marker)")
            }
        }
    }

    @objc public func closeViewer(webView: WKWebView?) {
        DispatchQueue.main.async {
            if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
                // hide pdfView to make hiding effect faster
                rootViewController.view.sendSubviewToBack(self.pdfView);

                // clear document
                self.pdfView.document = nil;
                if let observer = self.pageChangeObserver {
                    NotificationCenter.default.removeObserver(observer)
                    self.pageChangeObserver = nil
                }
                self.pageCount = 0
                self.currentPage = 0

                self.pdfView.frame = CGRect();
                self.pdfView.removeFromSuperview();

                // Restore WebView opacity
                webView?.isOpaque = true
                webView?.backgroundColor = .white
                webView?.scrollView.backgroundColor = .white
            }
        }
    }
    
    @objc public func getStatus() -> [String: Any] {
        let snapshot: () -> [String: Any] = {
            self.updateStatus()
            let isOpen = self.pdfView.document != nil
            let isAtEnd = isOpen && self.pageCount > 0 && self.currentPage >= self.pageCount - 1
            return [
                "isOpen": isOpen,
                "isAtEnd": isAtEnd,
                "page": self.currentPage,
                "pageCount": self.pageCount
            ]
        }
        
        if Thread.isMainThread {
            return snapshot()
        } else {
            return DispatchQueue.main.sync {
                return snapshot()
            }
        }
    }
    
    private func registerPageChangeObserver() {
        if let observer = pageChangeObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        pageChangeObserver = NotificationCenter.default.addObserver(
            forName: Notification.Name.PDFViewPageChanged,
            object: pdfView,
            queue: .main
        ) { [weak self] _ in
            self?.updateStatus()
        }
    }
    
    private func updateStatus() {
        guard let document = pdfView.document else {
            currentPage = 0
            pageCount = 0
            return
        }
        
        pageCount = document.pageCount
        if let page = pdfView.currentPage {
            currentPage = document.index(for: page)
        }
    }
}
