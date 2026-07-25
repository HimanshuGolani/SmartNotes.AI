import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import PdfViewer from './PdfViewer';
import ExcalidrawViewer from './ExcalidrawViewer';
import TopicsViewer from './TopicsViewer';
import { getPdfUrl, getExcalidrawUrl, downloadFile } from '../api/notesApi';

export default function ResultsViewer({ result, onReset }) {
  const hasTopics = Boolean(result.topics && result.topics.length > 0);
  const defaultTab = result.pdfPath ? 'pdf' : result.excalidrawPath ? 'excalidraw' : hasTopics ? 'topics' : 'pdf';
  const [activeTab, setActiveTab] = useState(defaultTab);

  const videoId = result.videoId;
  const hasPdf = Boolean(result.pdfPath);
  const hasExcalidraw = Boolean(result.excalidrawPath);

  const handleDownloadPdf = () => downloadFile(getPdfUrl(videoId), `${videoId}-notes.pdf`);
  const handleDownloadExcalidraw = () => downloadFile(getExcalidrawUrl(videoId), `${videoId}-notes.excalidraw`);

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -30 }}
      transition={{ duration: 0.6 }}
      style={{
        maxWidth: '1400px',
        margin: '0 auto',
        padding: '20px',
        position: 'relative',
        zIndex: 1,
      }}
    >
      {/* Header */}
      <div
        className="glass"
        style={{
          padding: '24px 32px',
          marginBottom: '20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '16px',
        }}
      >
        <div>
          <div
            style={{
              display: 'inline-block',
              padding: '4px 10px',
              background: 'rgba(16, 185, 129, 0.15)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
              borderRadius: '6px',
              fontSize: '11px',
              color: 'var(--success)',
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: '0.1em',
              marginBottom: '8px',
            }}
          >
            ✓ Complete
          </div>
          <h2
            className="gradient-text"
            style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}
          >
            {result.title || 'Notes Generated'}
          </h2>
          <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            {result.topicCount > 0 && `${result.topicCount} topics · `}
            {result.processingTimeSeconds > 0 && `${Math.round(result.processingTimeSeconds)}s processing time`}
            {result.cached && ' · loaded from cache'}
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          {hasPdf && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.97 }}
              onClick={handleDownloadPdf}
              className="btn-secondary"
            >
              📄 Download PDF
            </motion.button>
          )}
          {hasExcalidraw && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.97 }}
              onClick={handleDownloadExcalidraw}
              className="btn-secondary"
            >
              🎨 Download Excalidraw
            </motion.button>
          )}
          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.97 }}
            onClick={onReset}
            className="btn-primary"
            style={{ padding: '10px 20px', fontSize: '14px' }}
          >
            + New Video
          </motion.button>
        </div>
      </div>

      {/* Tabs */}
      <div
        style={{
          display: 'flex',
          gap: '8px',
          marginBottom: '16px',
          padding: '6px',
          background: 'rgba(0,0,0,0.3)',
          borderRadius: '12px',
          width: 'fit-content',
        }}
      >
        {hasPdf && (
          <TabButton
            active={activeTab === 'pdf'}
            onClick={() => setActiveTab('pdf')}
            icon="📄"
            label="PDF Document"
          />
        )}
        {hasExcalidraw && (
          <TabButton
            active={activeTab === 'excalidraw'}
            onClick={() => setActiveTab('excalidraw')}
            icon="🎨"
            label="Mind Map"
          />
        )}
        {hasTopics && (
          <TabButton
            active={activeTab === 'topics'}
            onClick={() => setActiveTab('topics')}
            icon="📋"
            label="Topics"
          />
        )}
      </div>

      {/* Viewer */}
      <div
        className="glass"
        style={{
          height: activeTab === 'topics' ? 'auto' : 'calc(100vh - 240px)',
          minHeight: '400px',
          padding: activeTab === 'topics' ? '16px' : '8px',
          overflow: activeTab === 'topics' ? 'visible' : 'hidden',
        }}
      >
        <AnimatePresence mode="wait">
          {activeTab === 'pdf' && hasPdf && (
            <motion.div
              key="pdf"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
              transition={{ duration: 0.3 }}
              style={{ height: '100%' }}
            >
              <PdfViewer url={getPdfUrl(videoId)} />
            </motion.div>
          )}
          {activeTab === 'excalidraw' && hasExcalidraw && (
            <motion.div
              key="excalidraw"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
              transition={{ duration: 0.3 }}
              style={{ height: '100%' }}
            >
              <ExcalidrawViewer videoId={videoId} />
            </motion.div>
          )}
          {activeTab === 'topics' && hasTopics && (
            <motion.div
              key="topics"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 20 }}
              transition={{ duration: 0.3 }}
            >
              <TopicsViewer topics={result.topics} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  );
}

function TabButton({ active, onClick, icon, label }) {
  return (
    <motion.button
      whileTap={{ scale: 0.97 }}
      onClick={onClick}
      style={{
        padding: '10px 20px',
        background: active ? 'linear-gradient(135deg, #7048e8, #1971c2)' : 'transparent',
        color: active ? 'white' : 'var(--text-secondary)',
        border: 'none',
        borderRadius: '8px',
        fontSize: '14px',
        fontWeight: 600,
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        cursor: 'pointer',
      }}
    >
      <span>{icon}</span>
      <span>{label}</span>
    </motion.button>
  );
}
