import Foundation
import UIKit
import PDFKit

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
                self.pdfView.frame = rootViewController.view.bounds;
                self.pdfView.frame.origin.y = CGFloat(top);
                // Adjust the height to account for the top padding
                self.pdfView.frame.size.height = rootViewController.view.bounds.height - CGFloat(top)

                rootViewController.view.bringSubviewToFront(self.pdfView);
                
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
    
    @objc public func closeViewer() {
        DispatchQueue.main.async {
            if let rootViewController = UIApplication.shared.keyWindow?.rootViewController {
                // hide pdfView to make hidding effect faster
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
