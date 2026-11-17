import { api } from '../api/client.js';
import usePolling from '../hooks/usePolling.js';
import ErrorNotice from '../components/ErrorNotice.jsx';
import { formatDateTime, timeAgo } from '../format.js';

export default function Workers() {
  const workers = usePolling(() => api.listWorkers(), 3000);
  const list = workers.data || [];
  const activeCount = list.filter((worker) => worker.active).length;

  return (
    <section>
      <div className="page-header">
        <h1>Workers</h1>
        <span className="muted">
          {activeCount} active, {list.length - activeCount} inactive
        </span>
      </div>

      <ErrorNotice error={workers.error} />

      <div className="panel">
        {workers.data && list.length === 0 && <p className="muted">No worker has registered yet.</p>}
        {list.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Worker</th>
                  <th>Status</th>
                  <th>Host</th>
                  <th>Last heartbeat</th>
                  <th>Workload</th>
                  <th>Completed</th>
                  <th>Failed</th>
                  <th>Started</th>
                </tr>
              </thead>
              <tbody>
                {list.map((worker) => (
                  <tr key={worker.id}>
                    <td>{worker.id}</td>
                    <td>
                      <span className={`badge ${worker.active ? 'badge-completed' : 'badge-cancelled'}`}>
                        {worker.active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                    <td>{worker.hostname}</td>
                    <td title={formatDateTime(worker.lastHeartbeatAt)}>{timeAgo(worker.lastHeartbeatAt)}</td>
                    <td>
                      <div className="meter" title={`${worker.currentExecutions} of ${worker.concurrency} slots in use`}>
                        <span className="meter-fill" style={{ width: `${(worker.currentExecutions / Math.max(worker.concurrency, 1)) * 100}%` }} />
                      </div>
                      <span className="muted">
                        {worker.currentExecutions} / {worker.concurrency}
                      </span>
                    </td>
                    <td>{worker.jobsCompleted}</td>
                    <td>{worker.jobsFailed}</td>
                    <td>{formatDateTime(worker.startedAt)}</td>
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
