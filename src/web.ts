import { WebPlugin } from '@capacitor/core';

import type { PDFViewerPlugin, PDFViewerStatus } from './definitions';

export class PDFViewerWeb extends WebPlugin implements PDFViewerPlugin {
  async open(): Promise<void> {
    throw new Error('[PDFViewerWeb] method not implemented');
  }

  async close(): Promise<void> {
    throw new Error('[PDFViewerWeb] method not implemented');
  }

  async getStatus(): Promise<PDFViewerStatus> {
    return {
      isOpen: false,
      isAtEnd: false,
      page: 0,
      pageCount: 0,
    };
  }
}
