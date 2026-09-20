export function LoadingState({ label = "Loading workspace" }: { label?: string }) {
  return <div className="loading-grid" role="status" aria-label={label}><span className="sr-only">{label}</span>{[0,1,2,3].map(n => <div className="loading-card" key={n}><i/><i/><i/></div>)}</div>;
}
