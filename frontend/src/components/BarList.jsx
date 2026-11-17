export default function BarList({ entries }) {
  const max = Math.max(1, ...entries.map(([, value]) => value));
  return (
    <ul className="bar-list">
      {entries.map(([label, value]) => (
        <li key={label}>
          <span className="bar-label">{label}</span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(value / max) * 100}%` }} />
          </span>
          <span className="bar-value">{value}</span>
        </li>
      ))}
    </ul>
  );
}
