import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

/** POST /api/notes/generate — returns { jobId } immediately (202 Accepted). */
export const generateNotes = async (payload) => {
  const { data } = await api.post('/api/notes/generate', payload);
  return data; // { jobId }
};

/** GET /api/notes/result/:jobId — returns NotesResponse, null (202 = still running), or throws on 404/5xx. */
export const fetchResult = async (jobId) => {
  const { data, status } = await api.get(`/api/notes/result/${jobId}`, {
    validateStatus: (s) => s < 500,
  });
  if (status === 404) throw new Error('Job not found — the session may have expired.');
  if (status === 202) return null;
  return data;
};

/**
 * Retries fetchResult up to maxAttempts times (1-second gap) until data is non-null.
 * Handles the rare race where the SSE "complete" event arrives slightly before
 * the result is queryable via the REST endpoint.
 */
export const fetchResultWithRetry = async (jobId, maxAttempts = 6) => {
  for (let i = 0; i < maxAttempts; i++) {
    const result = await fetchResult(jobId);
    if (result && typeof result === 'object' && result.topics !== undefined) return result;
    if (i < maxAttempts - 1) await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error('Result not available after retrying. Please refresh.');
};

/**
 * Opens an SSE connection to /api/notes/progress/:jobId.
 * Returns a cleanup function — call it to close the stream manually.
 *
 * callbacks:
 *   onProgress({ stage, total, message, percent }) — called on each progress tick
 *   onComplete()  — called when the server emits "complete"
 *   onError(msg)  — called on a server-side "error" event or SSE connection failure
 */
export const subscribeToProgress = (jobId, { onProgress, onComplete, onError }) => {
  const url = `${API_BASE}/api/notes/progress/${jobId}`;
  const es = new EventSource(url);
  let closed = false;

  const close = () => {
    if (!closed) { closed = true; es.close(); }
  };

  es.addEventListener('progress', (e) => {
    try { onProgress && onProgress(JSON.parse(e.data)); } catch {}
  });

  // Server heartbeat — keep-alive only, ignore in UI
  es.addEventListener('ping', () => {});

  es.addEventListener('complete', () => {
    close();
    onComplete && onComplete();
  });

  // Named "error" event = domain error from server (distinct from connection error)
  es.addEventListener('error', (e) => {
    close();
    try {
      const data = JSON.parse(e.data);
      onError && onError(data.message || 'Processing failed');
    } catch {
      onError && onError('Processing failed');
    }
  });

  // onerror = SSE transport error / connection dropped
  // readyState CONNECTING (0) means EventSource is auto-retrying — let it retry silently.
  // readyState CLOSED (2) means EventSource gave up — report the failure.
  es.onerror = (e) => {
    if (!closed && es.readyState === EventSource.CLOSED) {
      close();
      onError && onError('Lost connection to server. Please try again.');
    }
    // readyState === CONNECTING: EventSource is retrying — no action needed
  };

  return close; // caller can invoke to cancel
};

export const getPdfUrl = (videoId) =>
  `${API_BASE}/api/notes/download/pdf/${videoId}`;

export const getExcalidrawUrl = (videoId) =>
  `${API_BASE}/api/notes/download/excalidraw/${videoId}`;

export const downloadFile = async (url, filename) => {
  const response = await axios.get(url, { responseType: 'blob' });
  const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = blobUrl;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
};

export const fetchExcalidrawJson = async (videoId) => {
  const { data } = await axios.get(getExcalidrawUrl(videoId), {
    responseType: 'json',
  });
  return data;
};

export const checkHealth = async () => {
  const { data } = await api.get('/api/notes/health');
  return data;
};
