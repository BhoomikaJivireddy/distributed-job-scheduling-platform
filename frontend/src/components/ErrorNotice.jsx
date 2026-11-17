export default function ErrorNotice({ error }) {
  if (!error) {
    return null;
  }
  return (
    <div className="notice notice-error" role="alert">
      {error.message}
    </div>
  );
}
