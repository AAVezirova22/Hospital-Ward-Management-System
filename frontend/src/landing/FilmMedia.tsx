"use client";

import { useEffect, useState } from "react";
import { useReducedMotion } from "motion/react";

export function FilmMedia({
  still,
  video,
  alt,
  className,
  priority,
  drift,
}: {
  still: string;
  video?: string;
  alt: string;
  className?: string;
  priority?: boolean;
  drift?: boolean;
}) {
  const reduce = useReducedMotion();
  const [motionOn, setMotionOn] = useState(false);
  useEffect(() => {
    setMotionOn(!reduce);
  }, [reduce]);
  const playVideo = Boolean(video) && motionOn;
  return (
    <div className={`film-media ${className ?? ""}`.trim()}>
      <img
        src={still}
        alt={alt}
        className={`film-media-el ${!playVideo && drift && motionOn ? "is-drift" : ""}`}
        fetchPriority={priority ? "high" : "auto"}
        decoding={priority ? "sync" : "async"}
      />
      {playVideo ? (
        <video
          className="film-media-el film-media-video"
          autoPlay
          muted
          loop
          playsInline
          preload={priority ? "auto" : "metadata"}
          poster={still}
          aria-hidden="true"
        >
          <source src={video} type="video/mp4" />
        </video>
      ) : null}
    </div>
  );
}
