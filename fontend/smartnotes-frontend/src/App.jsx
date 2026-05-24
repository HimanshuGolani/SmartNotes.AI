import React, { useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import WelcomeScreen from './components/WelcomeScreen';
import UrlInputForm from './components/UrlInputForm';
import ProcessingStatus from './components/ProcessingStatus';
import ResultsViewer from './components/ResultsViewer';
import ParticleBackground from './components/ParticleBackground';
import { generateNotes } from './api/notesApi';

const STAGES = {
  WELCOME: 'welcome',
  INPUT: 'input',
  PROCESSING: 'processing',
  RESULTS: 'results',
  ERROR: 'error',
};

export default function App() {
  const [stage, setStage] = useState(STAGES.WELCOME);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [startedAt, setStartedAt] = useState(null);

  const handleSubmit = async (payload) => {
    setStage(STAGES.PROCESSING);
    setStartedAt(Date.now());
    setError(null);
    try {
      const data = await generateNotes(payload);
      setResult(data);
      setStage(STAGES.RESULTS);
    } catch (err) {
      console.error(err);
      setError(
        err?.response?.data?.message ||
          err?.message ||
          'Something went wrong. Check the backend logs.'
      );
      setStage(STAGES.ERROR);
    }
  };

  const handleReset = () => {
    setResult(null);
    setError(null);
    setStage(STAGES.INPUT);
  };

  return (
    <div className="app">
      <ParticleBackground />

      <AnimatePresence mode="wait">
        {stage === STAGES.WELCOME && (
          <WelcomeScreen
            key="welcome"
            onContinue={() => setStage(STAGES.INPUT)}
          />
        )}

        {stage === STAGES.INPUT && (
          <div
            key="input"
            style={{
              minHeight: '100vh',
              display: 'flex',
              alignItems: 'center',
              padding: '40px 20px',
            }}
          >
            <UrlInputForm onSubmit={handleSubmit} disabled={false} />
          </div>
        )}

        {stage === STAGES.PROCESSING && (
          <div
            key="processing"
            style={{
              minHeight: '100vh',
              display: 'flex',
              alignItems: 'center',
              padding: '40px 20px',
            }}
          >
            <ProcessingStatus startedAt={startedAt} />
          </div>
        )}

        {stage === STAGES.RESULTS && result && (
          <div key="results" style={{ minHeight: '100vh', padding: '40px 0' }}>
            <ResultsViewer result={result} onReset={handleReset} />
          </div>
        )}

        {stage === STAGES.ERROR && (
          <div
            key="error"
            style={{
              minHeight: '100vh',
              display: 'flex',
              alignItems: 'center',
              padding: '40px 20px',
            }}
          >
            <div
              className="glass"
              style={{
                maxWidth: '600px',
                margin: '0 auto',
                padding: '40px',
                textAlign: 'center',
                position: 'relative',
                zIndex: 1,
              }}
            >
              <div style={{ fontSize: '64px', marginBottom: '16px' }}>😔</div>
              <h2
                style={{
                  fontSize: '28px',
                  fontWeight: 700,
                  marginBottom: '12px',
                  color: 'var(--danger)',
                }}
              >
                Something went wrong
              </h2>
              <p
                style={{
                  color: 'var(--text-secondary)',
                  fontSize: '14px',
                  marginBottom: '24px',
                  fontFamily: "'JetBrains Mono', monospace",
                  background: 'rgba(0,0,0,0.3)',
                  padding: '12px',
                  borderRadius: '8px',
                  textAlign: 'left',
                  maxHeight: '200px',
                  overflow: 'auto',
                }}
              >
                {error}
              </p>
              <button onClick={handleReset} className="btn-primary">
                Try Again
              </button>
            </div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}