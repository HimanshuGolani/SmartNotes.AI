import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const formatTime = (sec) => {
  const s = Math.max(0, Math.floor(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

export default function TopicsViewer({ topics }) {
  const [expanded, setExpanded] = useState(null);

  if (!topics || topics.length === 0) {
    return (
      <div
        style={{
          padding: '40px',
          textAlign: 'center',
          color: 'var(--text-secondary)',
          fontSize: '15px',
        }}
      >
        No topics available for this video.
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '8px' }}>
      {topics.map((topic, idx) => {
        const isOpen = expanded === idx;
        const hasBullets = topic.bulletPoints && topic.bulletPoints.length > 0;
        const hasCaption = Boolean(topic.screenshotCaption);

        return (
          <motion.div
            key={idx}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.05 }}
            style={{
              background: isOpen ? 'rgba(112,72,232,0.08)' : 'rgba(255,255,255,0.03)',
              border: isOpen ? '1px solid rgba(112,72,232,0.35)' : '1px solid var(--border)',
              borderRadius: '12px',
              overflow: 'hidden',
              transition: 'border-color 0.2s, background 0.2s',
            }}
          >
            {/* Header — always visible */}
            <button
              onClick={() => setExpanded(isOpen ? null : idx)}
              style={{
                width: '100%',
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: '18px 20px',
                display: 'flex',
                alignItems: 'center',
                gap: '14px',
                textAlign: 'left',
              }}
            >
              {/* Topic number badge */}
              <div
                style={{
                  minWidth: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg, #7048e8, #1971c2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#fff',
                  fontSize: '13px',
                  fontWeight: 700,
                  flexShrink: 0,
                }}
              >
                {idx + 1}
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    fontSize: '15px',
                    fontWeight: 600,
                    color: 'var(--text-primary)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {topic.title || `Topic ${idx + 1}`}
                </div>
                <div
                  style={{
                    fontSize: '12px',
                    color: 'var(--text-muted)',
                    marginTop: '3px',
                  }}
                >
                  ⏱ {formatTime(topic.startTime)} — {formatTime(topic.endTime)}
                  {hasBullets && (
                    <span style={{ marginLeft: '12px' }}>
                      • {topic.bulletPoints.length} key points
                    </span>
                  )}
                </div>
              </div>

              <motion.div
                animate={{ rotate: isOpen ? 180 : 0 }}
                transition={{ duration: 0.2 }}
                style={{ color: 'var(--text-muted)', fontSize: '16px', flexShrink: 0 }}
              >
                ▾
              </motion.div>
            </button>

            {/* Expandable body */}
            <AnimatePresence initial={false}>
              {isOpen && (
                <motion.div
                  key="body"
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.25, ease: 'easeInOut' }}
                  style={{ overflow: 'hidden' }}
                >
                  <div style={{ padding: '0 20px 20px' }}>
                    {/* Summary */}
                    {topic.summary && (
                      <p
                        style={{
                          fontSize: '14px',
                          color: 'var(--text-secondary)',
                          lineHeight: 1.65,
                          marginBottom: hasBullets || hasCaption ? '16px' : 0,
                        }}
                      >
                        {topic.summary}
                      </p>
                    )}

                    {/* Bullet points */}
                    {hasBullets && (
                      <div style={{ marginBottom: hasCaption ? '16px' : 0 }}>
                        <div
                          style={{
                            fontSize: '11px',
                            fontWeight: 700,
                            color: 'var(--text-muted)',
                            textTransform: 'uppercase',
                            letterSpacing: '0.08em',
                            marginBottom: '8px',
                          }}
                        >
                          Key Points
                        </div>
                        <ul
                          style={{
                            listStyle: 'none',
                            padding: 0,
                            margin: 0,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '6px',
                          }}
                        >
                          {topic.bulletPoints.map((bp, bi) => (
                            <li
                              key={bi}
                              style={{
                                display: 'flex',
                                gap: '10px',
                                fontSize: '13px',
                                color: 'var(--text-secondary)',
                                lineHeight: 1.5,
                              }}
                            >
                              <span style={{ color: '#7048e8', flexShrink: 0, marginTop: '1px' }}>▸</span>
                              <span>{bp}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* Screenshot caption */}
                    {hasCaption && (
                      <div
                        style={{
                          padding: '12px 14px',
                          background: 'rgba(255,255,255,0.04)',
                          border: '1px dashed rgba(255,255,255,0.1)',
                          borderRadius: '8px',
                          display: 'flex',
                          gap: '10px',
                          alignItems: 'flex-start',
                        }}
                      >
                        <span style={{ fontSize: '18px', flexShrink: 0 }}>📸</span>
                        <p
                          style={{
                            fontSize: '13px',
                            color: 'var(--text-secondary)',
                            lineHeight: 1.55,
                            margin: 0,
                            fontStyle: 'italic',
                          }}
                        >
                          {topic.screenshotCaption}
                        </p>
                      </div>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        );
      })}
    </div>
  );
}
