import { useState } from 'react';
import { api } from '../api/client.js';
import { JOB_TYPES, PAYLOAD_HINTS, PAYLOAD_SAMPLES } from '../format.js';

const INITIAL = {
  name: '',
  jobType: 'TEXT_PROCESSING',
  payload: '',
  priority: 5,
  scheduledAt: '',
  maxRetries: 3,
  timeoutSeconds: 60,
  idempotencyKey: ''
};

export default function CreateJobForm({ onCreated, onClose }) {
  const [form, setForm] = useState(INITIAL);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  const fieldError = (field) => error?.fieldErrors.find((issue) => issue.field === field)?.message;

  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    const request = {
      name: form.name,
      jobType: form.jobType,
      payload: form.payload,
      priority: Number(form.priority),
      maxRetries: Number(form.maxRetries),
      timeoutSeconds: Number(form.timeoutSeconds)
    };
    if (form.scheduledAt) {
      request.scheduledAt = new Date(form.scheduledAt).toISOString();
    }
    if (form.idempotencyKey.trim()) {
      request.idempotencyKey = form.idempotencyKey.trim();
    }

    try {
      const result = await api.createJob(request);
      onCreated(result.body, result.replayed);
    } catch (failure) {
      setError(failure);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="panel form" onSubmit={submit}>
      <div className="panel-header">
        <h2>Create job</h2>
        <button type="button" className="button button-secondary" onClick={onClose}>
          Close
        </button>
      </div>

      {error && (
        <div className="notice notice-error" role="alert">
          {error.message}
        </div>
      )}

      <div className="form-grid">
        <label>
          Name
          <input value={form.name} onChange={update('name')} maxLength={150} required />
          {fieldError('name') && <span className="field-error">{fieldError('name')}</span>}
        </label>

        <label>
          Job type
          <select value={form.jobType} onChange={update('jobType')}>
            {JOB_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>

        <label className="span-2">
          Payload
          <textarea value={form.payload} onChange={update('payload')} rows={6} maxLength={10000} required />
          <span className="hint">
            {PAYLOAD_HINTS[form.jobType]}{' '}
            <button
              type="button"
              className="link-button"
              onClick={() => setForm({ ...form, payload: PAYLOAD_SAMPLES[form.jobType] })}
            >
              Use sample
            </button>
          </span>
          {fieldError('payload') && <span className="field-error">{fieldError('payload')}</span>}
        </label>

        <label>
          Priority (1 lowest, 10 highest)
          <input type="number" min="1" max="10" value={form.priority} onChange={update('priority')} />
          {fieldError('priority') && <span className="field-error">{fieldError('priority')}</span>}
        </label>

        <label>
          Scheduled execution time
          <input type="datetime-local" value={form.scheduledAt} onChange={update('scheduledAt')} />
          <span className="hint">Leave empty to run immediately.</span>
        </label>

        <label>
          Maximum retries
          <input type="number" min="0" max="10" value={form.maxRetries} onChange={update('maxRetries')} />
          {fieldError('maxRetries') && <span className="field-error">{fieldError('maxRetries')}</span>}
        </label>

        <label>
          Timeout (seconds)
          <input type="number" min="1" max="3600" value={form.timeoutSeconds} onChange={update('timeoutSeconds')} />
          {fieldError('timeoutSeconds') && <span className="field-error">{fieldError('timeoutSeconds')}</span>}
        </label>

        <label className="span-2">
          Idempotency key (optional)
          <input value={form.idempotencyKey} onChange={update('idempotencyKey')} maxLength={100} />
          <span className="hint">Submitting the same key with the same request returns the existing job instead of creating another.</span>
          {fieldError('idempotencyKey') && <span className="field-error">{fieldError('idempotencyKey')}</span>}
        </label>
      </div>

      <div className="form-actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'Submitting...' : 'Submit job'}
        </button>
      </div>
    </form>
  );
}
