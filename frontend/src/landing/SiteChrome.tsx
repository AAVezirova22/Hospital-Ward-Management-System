"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Moon,
  Sun,
  Pause,
  Play,
  X,
} from "@phosphor-icons/react";
import { AnimatePresence, motion } from "motion/react";
import { useSiteMotion } from "./SiteMotion";

const links = [
  { href: "#product", label: "Product" },
  { href: "#assistant", label: "Assistant" },
  { href: "#workflow", label: "Workflow" },
  { href: "#specialists", label: "Floor" },
];

export function SiteNav() {
  const menu = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [dark, setDark] = useState(false);
  const [active, setActive] = useState("#product");
  const { paused, reduced, toggle } = useSiteMotion();
  useEffect(() => {
    const system = matchMedia("(prefers-color-scheme: dark)");
    const update = () => {
      let theme: string | null = null;
      try {
        theme = localStorage.getItem("medcore-site-theme");
      } catch {}
      const isDark = theme ? theme === "dark" : system.matches;
      setDark(isDark);
      document.documentElement.dataset.siteTheme = isDark ? "dark" : "light";
    };
    update();
    system.addEventListener("change", update);
    return () => {
      system.removeEventListener("change", update);
      delete document.documentElement.dataset.siteTheme;
    };
  }, []);
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries)
          if (entry.isIntersecting) setActive(`#${entry.target.id}`);
      },
      { rootMargin: "-12% 0px -65% 0px" },
    );
    links.forEach((link) => {
      const section = document.querySelector(link.href);
      if (section) observer.observe(section);
    });
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);
  const close = () => {
    menu.current?.close();
    setOpen(false);
    opener.current?.focus();
  };
  const theme = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.dataset.siteTheme = next ? "dark" : "light";
    try {
      localStorage.setItem("medcore-site-theme", next ? "dark" : "light");
    } catch {}
  };
  return (
    <>
      <header className="mc-nav">
        <a href="#product" className="mc-brand" aria-label="Medcore home">
          Medcore<span aria-hidden="true">®</span>
        </a>
        <nav className="mc-nav-links" aria-label="Main navigation">
          {links.map((link) => (
            <a
              href={link.href}
              key={link.href}
              aria-current={active === link.href ? "location" : undefined}
            >
              {link.label}
            </a>
          ))}
        </nav>
        <div className="mc-nav-actions">
          <button
            className="mc-icon-button mc-motion-control"
            aria-label={paused ? "Enable animations" : "Pause animations"}
            aria-pressed={paused}
            onClick={toggle}
          >
            {paused ? <Play size={16} /> : <Pause size={16} />}
          </button>
          <button
            className="mc-icon-button"
            aria-label={dark ? "Use light theme" : "Use dark theme"}
            onClick={theme}
          >
            {dark ? <Sun size={18} /> : <Moon size={18} />}
          </button>
          <Link className="mc-nav-signin" href="/app">
            Sign in <ArrowUpRight size={15} aria-hidden="true" />
          </Link>
          <button
            className={`mc-menu-button ${open ? "is-open" : ""}`}
            ref={opener}
            aria-label="Open menu"
            aria-expanded={open}
            aria-controls="mc-menu"
            onClick={() => {
              menu.current?.showModal();
              setOpen(true);
            }}
          >
            <span />
            <span />
          </button>
        </div>
      </header>
      <dialog
        id="mc-menu"
        className="mc-menu"
        ref={menu}
        aria-label="Site menu"
        onCancel={() => setOpen(false)}
        onClose={() => setOpen(false)}
      >
        <div className="mc-menu-top">
          <a className="mc-brand" href="#product" onClick={close}>
            Medcore
          </a>
          <button
            className="mc-icon-button"
            aria-label="Close menu"
            onClick={close}
          >
            <X size={24} />
          </button>
        </div>
        <nav aria-label="Mobile navigation">
          {links
            .concat({ href: "#start", label: "Start" })
            .map((link, index) => (
              <motion.a
                key={link.href}
                href={link.href}
                onClick={close}
                initial={false}
                animate={{
                  y: open && !reduced ? 0 : 16,
                  opacity: open ? 1 : 0,
                }}
                transition={{
                  duration: reduced ? 0 : 0.5,
                  delay: reduced ? 0 : index * 0.04,
                  ease: [0.22, 1, 0.36, 1],
                }}
              >
                {link.label}
                <ArrowUpRight weight="light" />
              </motion.a>
            ))}
        </nav>
        <Link href="/app" className="mc-button" onClick={close}>
          Open the workspace
          <span>
            <ArrowUpRight />
          </span>
        </Link>
        <button className="mc-text-link" onClick={toggle}>
          {paused ? "Enable animations" : "Pause animations"}
        </button>
      </dialog>
    </>
  );
}

const tour = [
  {
    title: "Your ward, in one view.",
    text: "See admissions, available beds, your care team, and recent activity together. Open the workspace to explore your department.",
    image: "/landing/stills/workspace.webp",
    alt: "Medcore department dashboard showing synthetic demonstration data",
  },
  {
    title: "From files to a plan.",
    text: "Upload documents, connect a folder, or drop files into the assistant. Describe the workflow, review the proposed steps, and confirm when you're ready.",
    image: "/landing/stills/care.jpg",
    alt: "Care professional at work",
  },
  {
    title: "Stay connected with iMessage.",
    text: "Reach Medcore from a conversation. Keep your workflow moving between the ward and your workspace, with a human in control of changes.",
    image: "/landing/stills/presence.webp",
    alt: "Two care professionals reviewing a tablet together",
  },
];

export function Walkthrough() {
  const ref = useRef<HTMLDialogElement>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const [step, setStep] = useState(0);
  const { reduced } = useSiteMotion();
  useEffect(() => {
    const open = () => {
      if (location.hash !== "#booking") return;
      previousFocus.current = document.activeElement as HTMLElement;
      setStep(0);
      ref.current?.showModal();
    };
    const onLink = (event: MouseEvent) => {
      const link = (event.target as Element).closest('a[href="#booking"]');
      if (link && location.hash === "#booking") open();
    };
    open();
    window.addEventListener("hashchange", open);
    document.addEventListener("click", onLink);
    return () => {
      window.removeEventListener("hashchange", open);
      document.removeEventListener("click", onLink);
    };
  }, []);
  const close = () => {
    ref.current?.close();
    if (location.hash === "#booking")
      history.replaceState(null, "", location.pathname + location.search);
    previousFocus.current?.focus({ preventScroll: true });
  };
  const item = tour[step];
  return (
    <dialog
      className="mc-walkthrough"
      ref={ref}
      aria-labelledby="tour-title"
      onCancel={close}
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="mc-tour-content">
        <button
          className="mc-icon-button mc-dialog-close"
          aria-label="Close tour"
          onClick={close}
        >
          <X size={22} />
        </button>
        <div className={`mc-tour-photo ${step === 0 ? "is-workspace" : ""}`}>
          <Image
            src={item.image}
            alt={item.alt}
            fill
            sizes="(max-width: 767px) 100vw, 55vw"
          />
        </div>
        <div className="mc-tour-body">
          <p className="mc-eyebrow">
            Meet Medcore · {step + 1} of {tour.length}
          </p>
          <AnimatePresence mode="wait">
            <motion.div
              key={step}
              initial={reduced ? false : { opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: reduced ? 0 : 0.2 }}
            >
              <h2 id="tour-title">{item.title}</h2>
              <p>{item.text}</p>
            </motion.div>
          </AnimatePresence>
          <div className="mc-tour-controls">
            <button
              className="mc-icon-button"
              aria-label="Previous tour slide"
              disabled={step === 0}
              onClick={() => setStep((s) => s - 1)}
            >
              <ArrowLeft />
            </button>
            {step < tour.length - 1 ? (
              <button
                className="mc-button"
                onClick={() => setStep((s) => s + 1)}
              >
                Continue
                <span>
                  <ArrowRight />
                </span>
              </button>
            ) : (
              <Link href="/app" className="mc-button" onClick={close}>
                Open the workspace
                <span>
                  <ArrowUpRight />
                </span>
              </Link>
            )}
          </div>
        </div>
      </div>
    </dialog>
  );
}
