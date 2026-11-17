import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import usePolling from '../hooks/usePolling.js';
import StatusBadge from '../components/StatusBadge.jsx';
import ErrorNotice from '../components/ErrorNotice.jsx';
import { CANCELLABLE, formatDateTime, formatDuration } from '../format.js';

export default function JobDetails() {
  const { jobId } = useParams();
  const job = usePolling(() => api.getJob(jobId), 2000, [jobId]);
  const executions = usePolling(() => api.getExecutions(jobId), 2000, [jobId]);
  const [actionError, setActionError] = useState(null);

  const cancel = async () => {
    setActionError(null);
    try {
      await api.cancelJob(jobId);
      job.refresh();
      executions.refresh();
    } catch (failure) {
      setActionError(failure);
    }
  };

  if (job.error && !job.data) {
    return (
      <section>
        <Link to="/jobs" className="link">
          Back to jobs
        </Link>
        <ErrorNotice error={job.error} />
      </section>
    );
  }

  if (!job.data) {
    return <p className="muted">Loading job...</p>;
  }

  const data = job.data;
  const attempts = executions.data || [];

  return (
    <section>
      <Link to="/jobs" className="link">
        Back to jobs
      </Link>
      <div className="page-header">
        <h1>
          Job #{data.id} <StatusBadge status={data.status} />
        </h1>
        {CANCELLABLE.includes(data.status) && (
          <button type="button" className="button button-danger" onClick={cancel}>
            Cancel job
          </button>
        )}
      </div>

      <ErrorNotice error={actionError || executions.error} />

      <div className="panel">
        <h2>Job information</h2>
        <dl className="detail-grid">
          <div>
            <dt>Name</dt>
            <dd>{data.name}</dd>
          </div>
          <div>
            <dt>Type</dt>
            <dd>{data.jobType}</dd>
          </div>
          <div>
            <dt>Priority</dt>
            <dd>{data.priority}</dd>
          </div>
          <div>
            <dt>Retries used</dt>
            <dd>
              {data.retryCount} of {data.maxRetries}
            </dd>
          </div>
          <div>
            <dt>Timeout</dt>
            <dd>{data.timeoutSeconds} s</dd>
          </div>
          <div>
            <dt>Worker</dt>
            <dd>{data.workerId || '-'}</dd>
          </div>
          <div>
            <dt>Idempotency key</dt>
            <dd>{data.idempotencyKey || '-'}</dd>
          </div>
          <div>
            <dt>Scheduled for</dt>
            <dd>{formatDateTime(data.scheduledAt)}</dd>
          </div>
          <div>
            <dt>Next run at</dt>
            <dd>{formatDateTime(data.nextRunAt)}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{formatDateTime(data.createdAt)}</dd>
          </div>
          <div>
            <dt>Queued</dt>
            <dd>{formatDateTime(data.queuedAt)}</dd>
          </div>
          <div>
            <dt>Started</dt>
            <dd>{formatDateTime(data.startedAt)}</dd>
          </div>
          <div>
            <dt>Finished</dt>
            <dd>{formatDateTime(data.finishedAt)}</dd>
          </div>
          <div>
            <dt>Lease expires</dt>
            <dd>{formatDateTime(data.leaseExpiresAt)}</dd>
          </div>
        </dl>
        {data.lastError && (
          <div className="notice notice-error">
            <strong>Last error:</strong> {data.lastError}
          </div>
        )}
        <h3>Payload</h3>
        <pre className="code-block">{data.payload}</pre>
      </div>

      <div className="panel">
        <h2>Execution attempts</h2>
        {attempts.length === 0 && <p className="muted">This job has not been executed yet.</p>}
        {attempts.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Attempt</th>
                  <th>Worker</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Finished</th>
                  <th>Duration</th>
                </tr>
              </thead>
              <tbody>
                {attempts.map((attempt) => (
                  <tr key={attempt.id}>
                    <td>{attempt.attemptNumber}</td>
                    <td>{attempt.workerId}</td>
                    <td>
                      <StatusBadge status={attempt.status} />
                    </td>
                    <td>{formatDateTime(attempt.startedAt)}</td>
                    <td>{formatDateTime(attempt.finishedAt)}</td>
                    <td>{formatDuration(attempt.durationMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {attempts.some((attempt) => attempt.errorMessage || attempt.result) && (
        <div className="panel">
          <h2>Results and errors</h2>
          <ol className="history">
            {attempts.map((attempt) => (
              <li key={attempt.id}>
                <div className="history-title">
                  Attempt {attempt.attemptNumber} on {attempt.workerId} <StatusBadge status={attempt.status} />
                </div>
                {attempt.errorMessage && <p className="error-text">{attempt.errorMessage}</p>}
                {attempt.result && <pre className="code-block">{attempt.result}</pre>}
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}
