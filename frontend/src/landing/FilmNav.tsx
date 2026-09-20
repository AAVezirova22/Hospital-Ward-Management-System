"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { List, X } from "@phosphor-icons/react";
import Link from "next/link";

const LINKS = [
  { href: "#care", label: "Care" },
  { href: "#specialists", label: "Specialists" },
  { href: "#technology", label: "Technology" },
  { href: "#patients", label: "Patients" },
  { href: "#visit", label: "Visit" },
];

export function FilmNav() {
  const [open, setOpen] = useState(false);
  const reduce = useReducedMotion();
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);
  return (
    <>
      <header className="film-nav">
        <a className="film-mark" href="#care">
          Medcore
        </a>
        <div className="film-nav-end">
          <a className="film-nav-link" href="#specialists">
            Specialists
          </a>
          <button
            className="film-nav-menu"
            type="button"
            aria-expanded={open}
            aria-controls="film-menu"
            onClick={() => setOpen(true)}
          >
            Menu
            <List size={16} weight="light" />
          </button>
        </div>
      </header>
      <AnimatePresence>
        {open ? (
          <motion.div
            id="film-menu"
            className="film-overlay"
            role="dialog"
            aria-modal="true"
            aria-label="Site menu"
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.45, ease: [0.32, 0.72, 0, 1] }}
          >
            <button
              className="film-overlay-close"
              type="button"
              aria-label="Close menu"
              onClick={() => setOpen(false)}
            >
              <X size={28} weight="light" />
            </button>
            <ul className="film-overlay-list">
              {LINKS.map((link, index) => (
                <li key={link.href}>
                  <motion.a
                    href={link.href}
                    onClick={() => setOpen(false)}
                    initial={reduce ? false : { opacity: 0, y: 28 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{
                      delay: 0.08 + index * 0.05,
                      duration: 0.55,
                      ease: [0.32, 0.72, 0, 1],
                    }}
                  >
                    {link.label}
                  </motion.a>
                </li>
              ))}
              <li>
                <motion.div
                  initial={reduce ? false : { opacity: 0, y: 28 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{
                    delay: 0.4,
                    duration: 0.55,
                    ease: [0.32, 0.72, 0, 1],
                  }}
                >
                  <Link href="/app" onClick={() => setOpen(false)}>
                    Staff sign-in
                  </Link>
                </motion.div>
              </li>
            </ul>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </>
  );
}
