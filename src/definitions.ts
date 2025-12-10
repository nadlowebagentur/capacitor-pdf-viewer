export interface PDFViewerStatus {
  isOpen: boolean;
  isAtEnd: boolean;
  page: number;
  pageCount: number;
}

export interface PDFViewerPlugin {
  open(params: { url: string; title?: string; top?: number }): Promise<void>;
  close(): Promise<void>;
  getStatus(): Promise<PDFViewerStatus>;
}
