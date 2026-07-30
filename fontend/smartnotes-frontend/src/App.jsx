import React, { useState, useEffect } from 'react';
import { Routes, Route, Navigate, useNavigate, useParams, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import WelcomeScreen from './components/WelcomeScreen';
import UrlInputForm from './components/UrlInputForm';
import ProcessingStatus from './components/ProcessingStatus';
import ResultsViewer from './components/ResultsViewer';
import ParticleBackground from './components/ParticleBackground';
import { generateNotes, fetchResultWithRetry } from './api/notesApi';

// ── Route: Welcome ───────────────────────────────────────────────────────────

function WelcomeRoute() {
  const navigate = useNavigate();
  return <WelcomeScreen onContinue={() => navigate('/generate')} />;
}

// ── Route: Input form ────────────────────────────────────────────────────────

function GenerateRoute() {
  const navigate = useNavigate();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);

  // Errors from a failed processing job arrive via location.state.error
  const [banner, setBanner] = useState(location.state?.error ?? null);

  // Clear the state payload so a refresh doesn't re-show the error
  useEffect(() => {
    if (location.state?.error) {
      window.history.replaceState({}, '');
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSubmit = async (payload) => {
    setBanner(null);
    setSubmitting(true);
    try {
      const { jobId } = await generateNotes(payload);
      // Push → user can go back to the form from /processing
      navigate(`/processing/${jobId}`);
    } catch (err) {
      setBanner(
        err?.response?.data?.message ||
          err?.message ||
          'Could not start processing. Is the backend running?'
      );
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        padding: '40px 20px',
        position: 'relative',
        zIndex: 1,
      }}
    >
      {/* Error banner — shown when navigated back after a processing failure */}
      <AnimatePresence>
        {banner && (
          <motion.div
            key="banner"
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            style={{
              position: 'fixed',
              top: '20px',
              left: '50%',
              transform: 'translateX(-50%)',
              zIndex: 200,
              padding: '12px 20px',
              background: 'rgba(239, 68, 68, 0.12)',
              border: '1px solid rgba(239, 68, 68, 0.4)',
              borderRadius: '10px',
              color: 'var(--danger)',
              fontSize: '13px',
              maxWidth: '600px',
              width: '90%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '12px',
            }}
          >
            <span>⚠ {banner}</span>
            <button
              onClick={() => setBanner(null)}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--danger)',
                cursor: 'pointer',
                fontSize: '16px',
                padding: '0 4px',
                opacity: 0.7,
              }}
            >
              ×
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      <UrlInputForm onSubmit={handleSubmit} disabled={submitting} />
    </div>
  );
}

// ── Route: Processing ────────────────────────────────────────────────────────

function ProcessingRoute() {
  const { jobId } = useParams();
  const navigate = useNavigate();

  const handleComplete = () => {
    // replace: true removes /processing from the history stack so the back button
    // from /results goes to /generate, not back to a completed processing screen
    navigate(`/results/${jobId}`, { replace: true });
  };

  const handleError = (msg) => {
    // Return to the form; replace so history stays clean
    navigate('/generate', { replace: true, state: { error: msg } });
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        padding: '40px 20px',
        position: 'relative',
        zIndex: 1,
      }}
    >
      <ProcessingStatus
        jobId={jobId}
        onComplete={handleComplete}
        onError={handleError}
      />
    </div>
  );
}

// ── Route: Results ────────────────────────────────────────────────────────────

function ResultsRoute() {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchResultWithRetry(jobId)
      .then((data) => {
        if (data) {
          setResult(data);
        } else {
          // Result not ready — job may still be running; let ProcessingStatus handle it
          navigate(`/processing/${jobId}`, { replace: true });
        }
      })
      .catch((err) => {
        // Job expired or backend restarted
        navigate('/generate', {
          replace: true,
          state: { error: err?.message || 'Could not load results. The session may have expired.' },
        });
      })
      .finally(() => setLoading(false));
  }, [jobId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div style={{ textAlign: 'center' }}>
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
            style={{ fontSize: '52px', display: 'inline-block' }}
          >
            ⚙️
          </motion.div>
          <p style={{ color: 'var(--text-secondary)', marginTop: '16px', fontSize: '14px' }}>
            Loading your notes…
          </p>
        </div>
      </div>
    );
  }

  // Navigating away — render nothing while the redirect fires
  if (!result) return null;

  return (
    <div style={{ minHeight: '100vh', padding: '40px 0', position: 'relative', zIndex: 1 }}>
      <ResultsViewer result={result} onReset={() => navigate('/generate')} />
    </div>
  );
}

// ── App shell ─────────────────────────────────────────────────────────────────

export default function App() {
  const location = useLocation();

  return (
    <div className="app">
      <ParticleBackground />

      {/*
        Pass `location` + a stable key to Routes so AnimatePresence can detect
        route changes and run exit animations before mounting the next screen.
      */}
      <AnimatePresence mode="wait" initial={false}>
        <Routes location={location} key={location.pathname}>
          <Route path="/"                    element={<WelcomeRoute />} />
          <Route path="/generate"            element={<GenerateRoute />} />
          <Route path="/processing/:jobId"   element={<ProcessingRoute />} />
          <Route path="/results/:jobId"      element={<ResultsRoute />} />
          {/* Catch-all: any unknown URL goes to welcome */}
          <Route path="*"                    element={<Navigate to="/" replace />} />
        </Routes>
      </AnimatePresence>
    </div>
  );
}
