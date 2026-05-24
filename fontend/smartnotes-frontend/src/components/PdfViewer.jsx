import React, { useState } from 'react';
import { Document, Page, pdfjs } from 'react-pdf';
import { motion } from 'framer-motion';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

pdfjs.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

export default function PdfViewer({ url }) {
  const [numPages, setNumPages] = useState(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [scale, setScale] = useState(1.0);
  const [error, setError] = useState(null);

  const onLoadSuccess = ({ numPages }) => {
    setNumPages(numPages);
    setError(null);
  };

  const onLoadError = (err) => {
    console.error('PDF load error:', err);
    setError('Failed to load PDF. The backend may still be generating it.');
  };

  return (
    <div
      style={{
        background: 'rgba(0,0,0,0.3)',
        borderRadius: '12px',
        padding: '20px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Toolbar */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '16px',
          flexWrap: 'wrap',
          gap: '8px',
        }}
      >
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="btn-secondary"
            onClick={() => setPageNumber((p) => Math.max(1, p - 1))}
            disabled={pageNumber <= 1}
            style={{ padding: '8px 14px', fontSize: '13px' }}
          >
            ←
          </button>
          <span
            style={{
              padding: '8px 14px',
              fontSize: '13px',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--text-secondary)',
            }}
          >
            {pageNumber} / {numPages || '…'}
          </span>
          <button
            className="btn-secondary"
            onClick={() => setPageNumber((p) => Math.min(numPages, p + 1))}
            disabled={pageNumber >= numPages}
            style={{ padding: '8px 14px', fontSize: '13px' }}
          >
            →
          </button>
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="btn-secondary"
            onClick={() => setScale((s) => Math.max(0.5, s - 0.2))}
            style={{ padding: '8px 14px', fontSize: '13px' }}
          >
            −
          </button>
          <span
            style={{
              padding: '8px 14px',
              fontSize: '13px',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--text-secondary)',
            }}
          >
            {Math.round(scale * 100)}%
          </span>
          <button
            className="btn-secondary"
            onClick={() => setScale((s) => Math.min(2, s + 0.2))}
            style={{ padding: '8px 14px', fontSize: '13px' }}
          >
            +
          </button>
        </div>
      </div>

      {/* Document */}
      <div
        style={{
          flex: 1,
          overflow: 'auto',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'flex-start',
          padding: '8px',
        }}
      >
        {error ? (
          <div style={{ color: 'var(--danger)', textAlign: 'center', padding: '40px' }}>
            {error}
          </div>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5 }}
          >
            <Document
              file={url}
              onLoadSuccess={onLoadSuccess}
              onLoadError={onLoadError}
              loading={
                <div style={{ color: 'var(--text-secondary)', padding: '40px' }}>
                  Loading PDF…
                </div>
              }
            >
              <Page
                pageNumber={pageNumber}
                scale={scale}
                renderTextLayer={false}
                renderAnnotationLayer={false}
              />
            </Document>
          </motion.div>
        )}
      </div>
    </div>
  );
}