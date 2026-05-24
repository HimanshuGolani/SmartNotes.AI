import React, { useEffect, useState, Suspense, lazy } from 'react';
import { motion } from 'framer-motion';
import { fetchExcalidrawJson } from '../api/notesApi';
import '@excalidraw/excalidraw/index.css';  // v0.18+ CSS path

// Shim for any leftover Webpack-isms
if (typeof window !== 'undefined') {
  if (typeof window.process === 'undefined') {
    window.process = { env: { NODE_ENV: 'development' } };
  }
  if (typeof window.global === 'undefined') {
    window.global = window;
  }
}

const Excalidraw = lazy(() =>
  import('@excalidraw/excalidraw').then((mod) => ({ default: mod.Excalidraw }))
);

export default function ExcalidrawViewer({ videoId }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchExcalidrawJson(videoId)
      .then((json) => setData(json))
      .catch((err) => {
        console.error(err);
        setError('Failed to load Excalidraw scene.');
      });
  }, [videoId]);

  if (error) {
    return (
      <div
        style={{
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--danger, #ef4444)',
          padding: '40px',
          textAlign: 'center',
        }}
      >
        {error}
      </div>
    );
  }

  if (!data) {
    return (
      <div
        style={{
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--text-secondary, #888)',
          gap: '8px',
        }}
      >
        Loading scene
        <span className="loading-dots">
          <span />
          <span />
          <span />
        </span>
      </div>
    );
  }

  const initialData = {
    elements: data.elements || [],
    appState: {
      ...(data.appState || {}),
      viewBackgroundColor: data.appState?.viewBackgroundColor || '#f1f3f5',
      collaborators: new Map(),
    },
    files: data.files || {},
    scrollToContent: true,
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.5 }}
      style={{
        height: '100%',
        width: '100%',
        borderRadius: '12px',
        overflow: 'hidden',
        background: '#f1f3f5',
        position: 'relative',
      }}
    >
      <Suspense
        fallback={
          <div
            style={{
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#555',
            }}
          >
            Initializing canvas…
          </div>
        }
      >
        <ErrorBoundary>
          <Excalidraw
            initialData={initialData}
            viewModeEnabled={false}
            zenModeEnabled={false}
            gridModeEnabled={false}
            theme="light"
            UIOptions={{
              canvasActions: {
                loadScene: false,
                saveToActiveFile: false,
                export: { saveFileToDisk: true },
                toggleTheme: true,
                clearCanvas: false,
              },
            }}
          />
        </ErrorBoundary>
      </Suspense>
    </motion.div>
  );
}

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, errorMsg: '' };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, errorMsg: error.message || 'Unknown error' };
  }

  componentDidCatch(error, info) {
    console.error('Excalidraw crashed:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#666',
            padding: '40px',
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: '48px', marginBottom: '16px' }}>⚠️</div>
          <div style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>
            Excalidraw failed to render
          </div>
          <div style={{ fontSize: '13px', color: '#999', maxWidth: '400px' }}>
            {this.state.errorMsg}
          </div>
          <div style={{ fontSize: '12px', color: '#aaa', marginTop: '16px' }}>
            You can still download the .excalidraw file and open it at excalidraw.com
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}