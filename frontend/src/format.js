export function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

export function formatDuration(milliseconds) {
  if (milliseconds === null || milliseconds === undefined) {
    return '-';
  }
  if (milliseconds < 1000) {
    return `${milliseconds} ms`;
  }
  const seconds = milliseconds / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${Math.round(seconds % 60)}s`;
}

export function timeAgo(value) {
  if (!value) {
    return '-';
  }
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) {
    return `${seconds}s ago`;
  }
  if (seconds < 3600) {
    return `${Math.floor(seconds / 60)}m ago`;
  }
  if (seconds < 86400) {
    return `${Math.floor(seconds / 3600)}h ago`;
  }
  return `${Math.floor(seconds / 86400)}d ago`;
}

export function executionTime(job) {
  if (!job.startedAt) {
    return null;
  }
  if (job.status === 'RUNNING') {
    return Date.now() - new Date(job.startedAt).getTime();
  }
  if (job.finishedAt) {
    return new Date(job.finishedAt).getTime() - new Date(job.startedAt).getTime();
  }
  return null;
}

export const JOB_STATUSES = ['PENDING', 'SCHEDULED', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'RETRYING', 'CANCELLED', 'DEAD'];

export const JOB_TYPES = ['TEXT_PROCESSING', 'REPORT_GENERATION', 'DATA_PROCESSING', 'FAILURE_SIMULATION'];

export const CANCELLABLE = ['PENDING', 'SCHEDULED', 'QUEUED', 'RETRYING'];

export const PAYLOAD_HINTS = {
  TEXT_PROCESSING: 'Plain text. The worker counts words, sentences and the most frequent words.',
  REPORT_GENERATION: 'CSV text with a header row and at least one data row.',
  DATA_PROCESSING: 'Numbers separated by commas, spaces or new lines.',
  FAILURE_SIMULATION: 'key=value lines: failures=2 makes the first two attempts fail; delay_ms=500 adds a delay.'
};

export const PAYLOAD_SAMPLES = {
  TEXT_PROCESSING: 'The quick brown fox jumps over the lazy dog. The dog sleeps.',
  REPORT_GENERATION: 'region,units,price\nnorth,10,2.50\nsouth,20,3.50\nnorth,30,4.00',
  DATA_PROCESSING: '2, 4, 4, 4, 5, 5, 7, 9',
  FAILURE_SIMULATION: 'failures=2\ndelay_ms=500'
};
