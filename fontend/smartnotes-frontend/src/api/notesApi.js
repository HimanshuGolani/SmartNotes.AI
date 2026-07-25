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

/** GET /api/notes/result/:jobId — returns NotesResponse or 202 if still running. */
export const fetchResult = async (jobId) => {
  const { data } = await api.get(`/api/notes/result/${jobId}`);
  return data;
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
  es.onerror = () => {
    if (es.readyState === EventSource.CLOSED && !closed) {
      close();
      onError && onError('Lost connection to server. Please try again.');
    }
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
