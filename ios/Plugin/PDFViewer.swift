import Foundation
import UIKit
import PDFKit
import WebKit

@objc public class PDFViewer: NSObject {
    private let pdfView = PDFView()
    private var pageCount: Int = 0
    private var currentPage: Int = 0
    private var pageChangeObserver: NSObjectProtocol?
    private var savedWindowBackground: UIColor?
    
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
            guard let rootView = UIApplication.shared.keyWindow?.rootViewController?.view else { return }

            if mode == "back" {
                rootView.sendSubviewToBack(self.pdfView)
                // Make WebView fully transparent so PDF shows through.
                // Set the window background to white so the status bar gap (above PDFView frame)
                // is white instead of the window's default black.
                let window = UIApplication.shared.keyWindow
                self.savedWindowBackground = window?.backgroundColor
                window?.backgroundColor = .white
                webView?.isOpaque = false
                webView?.backgroundColor = .clear
                webView?.scrollView.isOpaque = false
                webView?.scrollView.backgroundColor = .clear
            } else {
                rootView.bringSubviewToFront(self.pdfView)
                UIApplication.shared.keyWindow?.backgroundColor = self.savedWindowBackground
                self.savedWindowBackground = nil
                webView?.isOpaque = true
                webView?.backgroundColor = .white
                webView?.scrollView.isOpaque = true
                webView?.scrollView.backgroundColor = .white
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

                // Restore WebView opacity and window background
                UIApplication.shared.keyWindow?.backgroundColor = self.savedWindowBackground
                self.savedWindowBackground = nil
                webView?.isOpaque = true
                webView?.backgroundColor = .white
                webView?.scrollView.isOpaque = true
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
