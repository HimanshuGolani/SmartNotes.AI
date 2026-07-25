import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const FRAME_INTERVAL_OPTIONS = [
  { value: '', label: 'Auto (recommended)' },
  { value: '5', label: 'Every 5 seconds (dense)' },
  { value: '15', label: 'Every 15 seconds' },
  { value: '30', label: 'Every 30 seconds (sparse)' },
];

export default function UrlInputForm({ onSubmit, disabled }) {
  const [url, setUrl] = useState('');
  const [exportPdf, setExportPdf] = useState(true);
  const [exportExcalidraw, setExportExcalidraw] = useState(true);
  const [error, setError] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [frameInterval, setFrameInterval] = useState('');
  const [forceRefresh, setForceRefresh] = useState(false);

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
      frameIntervalSeconds: frameInterval ? parseInt(frameInterval, 10) : null,
      forceRefresh,
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
          style={{ fontSize: '32px', fontWeight: 800, marginBottom: '8px' }}
        >
          Generate Smart Notes
        </h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
          Paste any YouTube URL and let AI do the magic
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        {/* URL input */}
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
            onChange={(e) => { setUrl(e.target.value); setError(''); }}
            disabled={disabled}
          />
        </div>

        {/* Export format toggles */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '12px',
            marginBottom: '16px',
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

        {/* Advanced toggle */}
        <div style={{ marginBottom: '20px' }}>
          <button
            type="button"
            onClick={() => setShowAdvanced((v) => !v)}
            disabled={disabled}
            style={{
              background: 'none',
              border: '1px solid var(--border)',
              borderRadius: '8px',
              padding: '8px 16px',
              color: 'var(--text-secondary)',
              fontSize: '13px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              width: '100%',
              justifyContent: 'space-between',
            }}
          >
            <span>⚙️ Advanced options</span>
            <motion.span
              animate={{ rotate: showAdvanced ? 180 : 0 }}
              transition={{ duration: 0.2 }}
            >
              ▾
            </motion.span>
          </button>

          <AnimatePresence initial={false}>
            {showAdvanced && (
              <motion.div
                key="advanced"
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.25 }}
                style={{ overflow: 'hidden' }}
              >
                <div
                  style={{
                    padding: '16px',
                    marginTop: '8px',
                    background: 'rgba(255,255,255,0.03)',
                    border: '1px solid var(--border)',
                    borderRadius: '10px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '16px',
                  }}
                >
                  {/* Frame interval */}
                  <div>
                    <label
                      style={{
                        display: 'block',
                        fontSize: '12px',
                        fontWeight: 600,
                        color: 'var(--text-secondary)',
                        marginBottom: '8px',
                        textTransform: 'uppercase',
                        letterSpacing: '0.06em',
                      }}
                    >
                      Frame capture interval
                    </label>
                    <select
                      value={frameInterval}
                      onChange={(e) => setFrameInterval(e.target.value)}
                      disabled={disabled}
                      style={{
                        width: '100%',
                        padding: '10px 12px',
                        background: 'rgba(255,255,255,0.05)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                        color: 'var(--text-primary)',
                        fontSize: '13px',
                      }}
                    >
                      {FRAME_INTERVAL_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Force refresh */}
                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      checked={forceRefresh}
                      onChange={(e) => setForceRefresh(e.target.checked)}
                      disabled={disabled}
                    />
                    <span style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                      <span>🔄 Force refresh</span>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 400 }}>
                        Ignore cached results and reprocess the video
                      </span>
                    </span>
                  </label>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
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
