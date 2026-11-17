import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import usePolling from '../hooks/usePolling.js';
import StatusBadge from '../components/StatusBadge.jsx';
import ErrorNotice from '../components/ErrorNotice.jsx';
import Pagination from '../components/Pagination.jsx';
import CreateJobForm from '../components/CreateJobForm.jsx';
import { CANCELLABLE, JOB_STATUSES, JOB_TYPES, executionTime, formatDateTime, formatDuration } from '../format.js';

const PAGE_SIZE = 15;

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'priority,desc', label: 'Priority high to low' },
  { value: 'priority,asc', label: 'Priority low to high' },
  { value: 'updatedAt,desc', label: 'Recently updated' }
];

export default function Jobs() {
  const [params, setParams] = useSearchParams();
  const [showForm, setShowForm] = useState(false);
  const [message, setMessage] = useState(null);
  const [actionError, setActionError] = useState(null);

  const status = params.get('status') || '';
  const jobType = params.get('jobType') || '';
  const sort = params.get('sort') || 'createdAt,desc';
  const page = Number(params.get('page') || 0);

  const jobs = usePolling(() => api.listJobs({ status, jobType, sort, page, size: PAGE_SIZE }), 3000, [status, jobType, sort, page]);

  const updateParam = (key, value) => {
    const next = new URLSearchParams(params);
    if (value === '' || value === null) {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    if (key !== 'page') {
      next.delete('page');
    }
    setParams(next);
  };

  const cancel = async (job) => {
    setActionError(null);
    try {
      await api.cancelJob(job.id);
      setMessage(`Job #${job.id} cancelled`);
      jobs.refresh();
    } catch (failure) {
      setActionError(failure);
    }
  };

  const created = (job, replayed) => {
    setShowForm(false);
    setMessage(replayed ? `Idempotency key matched existing job #${job.id}; no new job was created` : `Job #${job.id} submitted`);
    jobs.refresh();
  };

  return (
    <section>
      <div className="page-header">
        <h1>Jobs</h1>
        <button type="button" className="button" onClick={() => setShowForm(true)}>
          New job
        </button>
      </div>

      {showForm && <CreateJobForm onCreated={created} onClose={() => setShowForm(false)} />}
      {message && (
        <div className="notice notice-success" role="status">
          {message}
        </div>
      )}
      <ErrorNotice error={jobs.error || actionError} />

      <div className="filters">
        <label>
          Status
          <select value={status} onChange={(event) => updateParam('status', event.target.value)}>
            <option value="">All statuses</option>
            {JOB_STATUSES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
        <label>
          Job type
          <select value={jobType} onChange={(event) => updateParam('jobType', event.target.value)}>
            <option value="">All types</option>
            {JOB_TYPES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
        <label>
          Sort
          <select value={sort} onChange={(event) => updateParam('sort', event.target.value)}>
            {SORT_OPTIONS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="panel">
        {jobs.data && jobs.data.items.length === 0 && <p className="muted">No jobs match the current filters.</p>}
        {jobs.data && jobs.data.items.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Submitted</th>
                  <th>Execution time</th>
                  <th>Retries</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {jobs.data.items.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <Link to={`/jobs/${job.id}`} className="link">
                        #{job.id}
                      </Link>
                    </td>
                    <td>{job.name}</td>
                    <td>{job.jobType}</td>
                    <td>
                      <StatusBadge status={job.status} />
                    </td>
                    <td>{job.priority}</td>
                    <td>{formatDateTime(job.createdAt)}</td>
                    <td>{formatDuration(executionTime(job))}</td>
                    <td>
                      {job.retryCount} / {job.maxRetries}
                    </td>
                    <td className="actions">
                      <Link to={`/jobs/${job.id}`} className="button button-secondary button-small">
                        Details
                      </Link>
                      {CANCELLABLE.includes(job.status) && (
                        <button type="button" className="button button-danger button-small" onClick={() => cancel(job)}>
                          Cancel
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {jobs.data && (
          <Pagination
            page={jobs.data.page}
            totalPages={jobs.data.totalPages}
            totalElements={jobs.data.totalElements}
            onChange={(next) => updateParam('page', String(next))}
          />
        )}
      </div>
    </section>
  );
}
