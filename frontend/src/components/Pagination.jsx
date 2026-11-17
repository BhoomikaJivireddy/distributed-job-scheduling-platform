export default function Pagination({ page, totalPages, totalElements, onChange }) {
  return (
    <div className="pagination">
      <span className="muted">
        {totalElements} {totalElements === 1 ? 'job' : 'jobs'}
      </span>
      <div className="pagination-controls">
        <button type="button" className="button button-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          Previous
        </button>
        <span>
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <button
          type="button"
          className="button button-secondary"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}
