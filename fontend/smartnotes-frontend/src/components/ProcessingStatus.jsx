import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { subscribeToProgress, fetchResultWithRetry } from '../api/notesApi';

const STAGE_ICONS = ['🔍', '⬇️', '🖼️', '🎙️', '🔗', '🧠', '👁️', '📄'];
const STAGE_LABELS = [
  'Checking cache',
  'Downloading video',
  'Extracting frames',
  'Transcribing audio',
  'Aligning context',
  'Generating topics with AI',
  'Analysing key frames',
  'Exporting documents',
];

const formatTime = (s) => {
  const m = Math.floor(s / 60);
  const sec = s % 60;
  return `${m}:${String(sec).padStart(2, '0')}`;
};

export default function ProcessingStatus({ jobId, onComplete, onError }) {
  const [elapsed, setElapsed] = useState(0);
  const [activeStage, setActiveStage] = useState(0);
  const [stageMessage, setStageMessage] = useState('Starting…');
  const [percent, setPercent] = useState(0);
  const [fromCache, setFromCache] = useState(false);
  const startedAtRef = useRef(Date.now());
  const cleanupRef = useRef(null);

  // Elapsed timer
  useEffect(() => {
    const t = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startedAtRef.current) / 1000));
    }, 1000);
    return () => clearInterval(t);
  }, []);

  // SSE subscription
  useEffect(() => {
    if (!jobId) return;

    const cleanup = subscribeToProgress(jobId, {
      onProgress: ({ stage, message, percent: pct }) => {
        setActiveStage(Math.min(stage, STAGE_LABELS.length - 1));
        setStageMessage(message);
        setPercent(pct);
      },
      onComplete: async () => {
        const wasInstant = elapsed < 3;
        setPercent(100);
        setStageMessage(wasInstant ? 'Loaded from cache!' : 'Done!');
        if (wasInstant) setFromCache(true);
        try {
          const result = await fetchResultWithRetry(jobId);
          onComplete && onComplete(result);
        } catch (e) {
          onError && onError('Processing finished but result could not be fetched: ' + e.message);
        }
      },
      onError: (msg) => {
        onError && onError(msg);
      },
    });

    cleanupRef.current = cleanup;
    return () => cleanup();
  }, [jobId]);

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
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ duration: 8, repeat: Infinity, ease: 'linear' }}
          style={{ fontSize: '56px', display: 'inline-block' }}
        >
          ⚙️
        </motion.div>
        <h2
          className="gradient-text"
          style={{ fontSize: '28px', fontWeight: 800, marginTop: '12px' }}
        >
          {fromCache ? 'Notes ready!' : 'Crafting your notes'}
        </h2>
        {fromCache && (
          <div style={{
            display: 'inline-block',
            marginTop: '6px',
            padding: '4px 12px',
            background: 'rgba(52, 211, 153, 0.15)',
            border: '1px solid rgba(52, 211, 153, 0.4)',
            borderRadius: '20px',
            fontSize: '12px',
            color: '#34d399',
            fontWeight: 600,
          }}>
            Served from cache
          </div>
        )}
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px', marginTop: '8px' }}>
          {stageMessage}
        </p>
      </div>

      {/* Progress bar */}
      <div
        style={{
          width: '100%',
          height: '8px',
          background: 'rgba(255,255,255,0.06)',
          borderRadius: '4px',
          overflow: 'hidden',
          marginBottom: '32px',
        }}
      >
        <motion.div
          animate={{ width: `${percent}%` }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          style={{
            height: '100%',
            background: 'linear-gradient(90deg, #7048e8, #60a5fa, #34d399)',
            borderRadius: '4px',
          }}
        />
      </div>

      {/* Time */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '16px',
          marginBottom: '32px',
        }}
      >
        <div
          style={{
            padding: '16px',
            background: 'rgba(255,255,255,0.04)',
            borderRadius: '10px',
            textAlign: 'center',
          }}
        >
          <div
            style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.1em' }}
          >
            Elapsed
          </div>
          <div
            style={{
              fontSize: '28px',
              fontWeight: 700,
              color: 'var(--accent-glow)',
              fontFamily: "'JetBrains Mono', monospace",
              marginTop: '4px',
            }}
          >
            {formatTime(elapsed)}
          </div>
        </div>
        <div
          style={{
            padding: '16px',
            background: 'rgba(255,255,255,0.04)',
            borderRadius: '10px',
            textAlign: 'center',
          }}
        >
          <div
            style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.1em' }}
          >
            Progress
          </div>
          <div
            style={{
              fontSize: '28px',
              fontWeight: 700,
              color: '#60a5fa',
              fontFamily: "'JetBrains Mono', monospace",
              marginTop: '4px',
            }}
          >
            {percent}%
          </div>
        </div>
      </div>

      {/* Stages */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {STAGE_LABELS.map((label, idx) => {
          const isActive = idx === activeStage;
          const isDone = idx < activeStage;
          return (
            <motion.div
              key={idx}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: idx * 0.04 }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '14px',
                padding: '12px 16px',
                background: isActive
                  ? 'rgba(112, 72, 232, 0.15)'
                  : isDone
                  ? 'rgba(16, 185, 129, 0.08)'
                  : 'rgba(255,255,255,0.02)',
                border: isActive
                  ? '1px solid rgba(112, 72, 232, 0.4)'
                  : '1px solid var(--border)',
                borderRadius: '10px',
                transition: 'all 0.3s',
              }}
            >
              <div style={{ fontSize: '20px' }}>
                {isDone ? '✅' : isActive ? STAGE_ICONS[idx] : '⏳'}
              </div>
              <div style={{ flex: 1, fontSize: '14px', fontWeight: isActive ? 600 : 400 }}>
                {label}
              </div>
              {isActive && (
                <div className="loading-dots">
                  <span />
                  <span />
                  <span />
                </div>
              )}
            </motion.div>
          );
        })}
      </div>

      <div
        style={{
          marginTop: '24px',
          padding: '14px',
          background: 'rgba(245, 158, 11, 0.08)',
          border: '1px solid rgba(245, 158, 11, 0.2)',
          borderRadius: '10px',
          fontSize: '13px',
          color: 'var(--text-secondary)',
          textAlign: 'center',
        }}
      >
        ⏱ Long videos take longer. The AI is doing real work — please wait.
      </div>
    </motion.div>
  );
}
