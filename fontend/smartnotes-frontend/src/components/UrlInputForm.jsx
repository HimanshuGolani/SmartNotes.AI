import React, { useState } from 'react';
import { motion } from 'framer-motion';

export default function UrlInputForm({ onSubmit, disabled }) {
  const [url, setUrl] = useState('');
  const [exportPdf, setExportPdf] = useState(true);
  const [exportExcalidraw, setExportExcalidraw] = useState(true);
  const [error, setError] = useState('');

  const validate = (value) => {
    const ytPattern = /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.be)\/.+/;
    return ytPattern.test(value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setError('');
    if (!url.trim()) {
      setError('Please enter a YouTube URL');
      return;
    }
    if (!validate(url)) {
      setError('Please enter a valid YouTube URL');
      return;
    }
    if (!exportPdf && !exportExcalidraw) {
      setError('Select at least one export format');
      return;
    }
    onSubmit({
      youtubeUrl: url.trim(),
      exportPdf,
      exportExcalidraw,
    });
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -30 }}
      transition={{ duration: 0.6 }}
      className="glass"
      style={{
        maxWidth: '720px',
        margin: '0 auto',
        padding: '40px',
        position: 'relative',
        zIndex: 1,
      }}
    >
      <div style={{ textAlign: 'center', marginBottom: '32px' }}>
        <h2
          className="gradient-text"
          style={{
            fontSize: '32px',
            fontWeight: 800,
            marginBottom: '8px',
          }}
        >
          Generate Smart Notes
        </h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
          Paste any YouTube URL and let AI do the magic
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '20px' }}>
          <label
            style={{
              display: 'block',
              fontSize: '13px',
              fontWeight: 500,
              color: 'var(--text-secondary)',
              marginBottom: '8px',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
            }}
          >
            YouTube URL
          </label>
          <input
            type="url"
            placeholder="https://www.youtube.com/watch?v=..."
            value={url}
            onChange={(e) => {
              setUrl(e.target.value);
              setError('');
            }}
            disabled={disabled}
          />
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '12px',
            marginBottom: '24px',
          }}
        >
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={exportPdf}
              onChange={(e) => setExportPdf(e.target.checked)}
              disabled={disabled}
            />
            <span>📄 Export PDF</span>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={exportExcalidraw}
              onChange={(e) => setExportExcalidraw(e.target.checked)}
              disabled={disabled}
            />
            <span>🎨 Export Excalidraw</span>
          </label>
        </div>

        {error && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            style={{
              padding: '12px 16px',
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: '8px',
              color: 'var(--danger)',
              fontSize: '14px',
              marginBottom: '20px',
            }}
          >
            ⚠ {error}
          </motion.div>
        )}

        <motion.button
          type="submit"
          disabled={disabled}
          whileHover={!disabled ? { scale: 1.02 } : {}}
          whileTap={!disabled ? { scale: 0.98 } : {}}
          className="btn-primary"
          style={{ width: '100%' }}
        >
          {disabled ? 'Processing…' : '✨ Generate Notes'}
        </motion.button>
      </form>

      <div
        style={{
          marginTop: '24px',
          padding: '16px',
          background: 'rgba(112, 72, 232, 0.08)',
          borderRadius: '10px',
          fontSize: '13px',
          color: 'var(--text-secondary)',
          lineHeight: 1.6,
        }}
      >
        💡 <strong style={{ color: 'var(--text-primary)' }}>Tip:</strong>{' '}
        Processing a 10-minute video typically takes 1–3 minutes. Longer videos
        scale linearly. Hold tight!
      </div>
    </motion.div>
  );
}