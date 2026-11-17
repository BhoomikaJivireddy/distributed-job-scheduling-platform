import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import usePolling from '../hooks/usePolling.js';
import StatCard from '../components/StatCard.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import ErrorNotice from '../components/ErrorNotice.jsx';
import { formatDateTime, timeAgo } from '../format.js';

const POLL_MS = 3000;

export default function Dashboard() {
  const stats = usePolling(() => api.getStats(), POLL_MS);
  const recent = usePolling(() => api.listJobs({ page: 0, size: 8, sort: 'updatedAt,desc' }), POLL_MS);

  const data = stats.data;

  return (
    <section>
      <div className="page-header">
        <h1>Dashboard</h1>
        <span className="muted">Refreshes every {POLL_MS / 1000} seconds</span>
      </div>

      <ErrorNotice error={stats.error || recent.error} />

      <div className="stat-grid">
        <StatCard label="Total jobs" value={data ? data.totalJobs : '-'} />
        <StatCard label="Pending jobs" value={data ? data.pendingJobs : '-'} tone="warning" />
        <StatCard label="Running jobs" value={data ? data.runningJobs : '-'} tone="info" />
        <StatCard label="Completed jobs" value={data ? data.completedJobs : '-'} tone="success" />
        <StatCard label="Failed jobs" value={data ? data.failedJobs : '-'} tone="danger" />
        <StatCard label="Active workers" value={data ? data.activeWorkers : '-'} tone="success" />
      </div>

      <div className="panel">
        <div className="panel-header">
          <h2>Recent job activity</h2>
          <Link to="/jobs" className="link">
            All jobs
          </Link>
        </div>
        {recent.data && recent.data.items.length === 0 && <p className="muted">No jobs have been submitted yet.</p>}
        {recent.data && recent.data.items.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Worker</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {recent.data.items.map((job) => (
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
                    <td>{job.workerId || '-'}</td>
                    <td title={formatDateTime(job.updatedAt)}>{timeAgo(job.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
