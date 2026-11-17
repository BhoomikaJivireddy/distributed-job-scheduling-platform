const BASE = '/api/v1';

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || [];
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
  } catch (networkError) {
    throw new ApiError('The server could not be reached', 0);
  }

  const text = await response.text();
  const body = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(body?.message || `Request failed with status ${response.status}`, response.status, body?.fieldErrors);
  }
  return { body, status: response.status, replayed: response.headers.get('Idempotent-Replayed') === 'true' };
}

function queryString(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value);
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

export const api = {
  listJobs: (params) => request(`/jobs${queryString(params)}`).then((r) => r.body),
  getJob: (id) => request(`/jobs/${id}`).then((r) => r.body),
  getExecutions: (id) => request(`/jobs/${id}/executions`).then((r) => r.body),
  createJob: (payload) => request('/jobs', { method: 'POST', body: JSON.stringify(payload) }),
  cancelJob: (id) => request(`/jobs/${id}/cancel`, { method: 'POST' }).then((r) => r.body),
  listWorkers: () => request('/workers').then((r) => r.body),
  getStats: () => request('/stats').then((r) => r.body)
};
