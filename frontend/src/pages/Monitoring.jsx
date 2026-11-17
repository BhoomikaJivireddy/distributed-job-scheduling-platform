import { api } from '../api/client.js';
import usePolling from '../hooks/usePolling.js';
import StatCard from '../components/StatCard.jsx';
import BarList from '../components/BarList.jsx';
import ErrorNotice from '../components/ErrorNotice.jsx';
import { formatDuration } from '../format.js';

const GRAFANA_URL = import.meta.env.VITE_GRAFANA_URL || 'http://localhost:3000';

export default function Monitoring() {
  const stats = usePolling(() => api.getStats(), 5000);
  const data = stats.data;

  return (
    <section>
      <div className="page-header">
        <h1>Monitoring</h1>
        <a className="button button-secondary" href={GRAFANA_URL} target="_blank" rel="noreferrer">
          Open Grafana
        </a>
      </div>

      <ErrorNotice error={stats.error} />

      {data && (
        <>
          <div className="stat-grid">
            <StatCard label="Executions in the last hour" value={data.executionsLastHour} />
            <StatCard label="Average completed duration" value={formatDuration(data.averageCompletedDurationMs)} />
            <StatCard label="Total retries" value={data.totalRetries} tone="warning" />
            <StatCard label="Active workers" value={data.activeWorkers} tone="success" />
            <StatCard label="Inactive workers" value={data.inactiveWorkers} tone="danger" />
          </div>

          <div className="two-column">
            <div className="panel">
              <h2>Jobs by status</h2>
              <BarList entries={Object.entries(data.jobsByStatus)} />
            </div>
            <div className="panel">
              <h2>Jobs by type</h2>
              <BarList entries={Object.entries(data.jobsByType)} />
            </div>
            <div className="panel">
              <h2>Execution attempts by outcome</h2>
              <BarList entries={Object.entries(data.executionsByStatus)} />
            </div>
          </div>
        </>
      )}
    </section>
  );
}
