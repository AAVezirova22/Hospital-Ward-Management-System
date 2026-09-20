export function FilmMedia({
  still,
  alt,
  className,
  priority,
}: {
  still: string;
  alt: string;
  className?: string;
  priority?: boolean;
}) {
  return (
    <div className={`film-media ${className ?? ""}`.trim()}>
      <img
        src={still}
        alt={alt}
        className="film-media-el"
        fetchPriority={priority ? "high" : "auto"}
        decoding={priority ? "sync" : "async"}
      />
    </div>
  );
}
