import React from 'react';
import { motion } from 'framer-motion';
import Globe3D from './Globe3D';
import { useTypewriter } from '../hooks/useTypewriter';

export default function WelcomeScreen({ onContinue }) {
  const { displayed: title, done: titleDone } = useTypewriter(
    'Welcome to SmartNotes.ai',
    60,
    800
  );
  const { displayed: subtitle } = useTypewriter(
    'by Himanshu Golani',
    50,
    titleDone ? 200 : 4000
  );

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.8 }}
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 20px',
        position: 'relative',
        zIndex: 1,
      }}
    >
      <motion.div
        initial={{ scale: 0, rotate: -180 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{ duration: 1.2, type: 'spring', stiffness: 60 }}
      >
        <Globe3D size={420} />
      </motion.div>

      <div style={{ textAlign: 'center', marginTop: '20px' }}>
        <h1
          className="gradient-text"
          style={{
            fontSize: 'clamp(32px, 5vw, 56px)',
            fontWeight: 800,
            letterSpacing: '-0.02em',
            minHeight: '1.2em',
          }}
        >
          {title}
          {!titleDone && <span className="cursor"></span>}
        </h1>
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: titleDone ? 1 : 0 }}
          style={{
            fontSize: 'clamp(16px, 2vw, 20px)',
            color: 'var(--text-secondary)',
            marginTop: '12px',
            fontWeight: 300,
            letterSpacing: '0.05em',
            minHeight: '1.5em',
          }}
        >
          {subtitle}
        </motion.p>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: titleDone ? 1 : 0, y: titleDone ? 0 : 20 }}
        transition={{ delay: 1.5, duration: 0.6 }}
        style={{ marginTop: '60px', textAlign: 'center' }}
      >
        <p
          style={{
            color: 'var(--text-muted)',
            fontSize: '14px',
            marginBottom: '24px',
            maxWidth: '500px',
          }}
        >
          Transform any YouTube video into beautifully structured notes,
          interactive mind-maps, and shareable PDFs — powered by AI.
        </p>
        <motion.button
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.97 }}
          onClick={onContinue}
          className="btn-primary"
          style={{ fontSize: '16px', padding: '16px 40px' }}
        >
          Begin Journey →
        </motion.button>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: titleDone ? 0.5 : 0 }}
        transition={{ delay: 2 }}
        style={{
          position: 'absolute',
          bottom: '24px',
          color: 'var(--text-muted)',
          fontSize: '12px',
          fontFamily: "'JetBrains Mono', monospace",
        }}
      >
        v1.0 · React · Three.js · Spring AI
      </motion.div>
    </motion.div>
  );
}