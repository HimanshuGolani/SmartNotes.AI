import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE,
  timeout: 600000, // 10 minutes - long videos
  headers: { 'Content-Type': 'application/json' },
});

export const generateNotes = async (payload) => {
  const { data } = await api.post('/api/notes/generate', payload);
  return data;
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