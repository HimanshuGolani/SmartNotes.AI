import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';

const STAGES = [
  { id: 1, label: 'Downloading metadata', icon: '🌐', duration: 5 },
  { id: 2, label: 'Extracting transcript', icon: '🎙️', duration: 10 },
  { id: 3, label: 'Capturing key frames', icon: '🖼️', duration: 15 },
  { id: 4, label: 'Vision analysis', icon: '👁️', duration: 30 },
  { id: 5, label: 'Aligning context', icon: '🔗', duration: 5 },
  { id: 6, label: 'Generating topics with AI', icon: '🧠', duration: 60 },
  { id: 7, label: 'Crafting PDF document', icon: '📄', duration: 8 },
  { id: 8, label: 'Building mind-map', icon: '🎨', duration: 10 },
];

const formatTime = (s) => {
  const m = Math.floor(s / 60);
  const sec = s % 60;
  return `${m}:${String(sec).padStart(2, '0')}`;
};

export default function ProcessingStatus({ startedAt }) {
  const [elapsed, setElapsed] = useState(0);
  const [activeStage, setActiveStage] = useState(0);

  useEffect(() => {
    const t = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => clearInterval(t);
  }, [startedAt]);

  useEffect(() => {
    let cumulative = 0;
    let stage = 0;
    for (let i = 0; i < STAGES.length; i++) {
      cumulative += STAGES[i].duration;
      if (elapsed < cumulative) {
        stage = i;
        break;
      }
      stage = i;
    }
    setActiveStage(stage);
  }, [elapsed]);

  const totalEstimate = STAGES.reduce((acc, s) => acc + s.duration, 0);
  const progress = Math.min(100, (elapsed / totalEstimate) * 100);

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
          Crafting your notes
        </h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: '14px', marginTop: '8px' }}>
          AI is hard at work. Sit back and relax.
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
          initial={{ width: '0%' }}
          animate={{ width: `${progress}%` }}
          transition={{ duration: 1, ease: 'easeOut' }}
          style={{
            height: '100%',
            background: 'linear-gradient(90deg, #7048e8, #60a5fa, #34d399)',
            backgroundSize: '200% 100%',
            animation: 'gradient-shift 3s ease infinite',
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
            Est. Remaining
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
            ~{formatTime(Math.max(0, totalEstimate - elapsed))}
          </div>
        </div>
      </div>

      {/* Stages */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {STAGES.map((stage, idx) => {
          const isActive = idx === activeStage;
          const isDone = idx < activeStage;
          return (
            <motion.div
              key={stage.id}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: idx * 0.05 }}
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
                {isDone ? '✅' : isActive ? stage.icon : '⏳'}
              </div>
              <div style={{ flex: 1, fontSize: '14px', fontWeight: isActive ? 600 : 400 }}>
                {stage.label}
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
        ⏱ Stage durations are estimates. Long videos take longer.
      </div>
    </motion.div>
  );
}