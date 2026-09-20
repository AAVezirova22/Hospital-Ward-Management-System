"use client";
import React, { useEffect, useState } from "react";
import NextLink from "next/link";
import { usePathname } from "next/navigation";
import { motion, AnimatePresence, LayoutGroup } from "motion/react";
import {
  Activity,
  LayoutDashboard,
  Users,
  BedDouble,
  Stethoscope,
  ClipboardList,
  ChartNoAxesCombined,
  ShieldCheck,
  Search,
  LogOut,
  MoveRight,
  CheckCircle2,
  Sparkles,
  Menu,
  History,
  Presentation,
} from "lucide-react";
import { Link, useUser } from "./workspace";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";
import { DemoReset } from "../features/demo/DemoAccess";
import { Assistant } from "../features/assistant/Assistant";
import { NotificationCenter } from "./NotificationCenter";
import { LiveOperations } from "./LiveOperations";
import { MobileNavigation } from "./MobileNavigation";
import { IdleTimeout } from "./IdleTimeout";
import {
  ThemeToggle,
  MotionToggle,
  SceneTransition,
  useCinematicMotion,
} from "../cinematic";

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const pathname = usePathname();
  const animated = useCinematicMotion();
  const active = pathname === to || pathname.startsWith(to + "/");
  return (
    <NextLink
      href={to}
      className={active ? "active" : undefined}
      aria-current={active ? "page" : undefined}
    >
      {active && (
        <motion.span
          layoutId={animated ? "sidebar-selection" : undefined}
          className="nav-selection"
          aria-hidden="true"
          transition={{
            type: "spring",
            stiffness: 260,
            damping: 32,
            mass: 0.7,
          }}
        />
      )}
      {children}
    </NextLink>
  );
}

const nav = [
  ["dashboard", "Overview", LayoutDashboard],
  ["patients", "Patients", Users],
  ["admissions", "Admissions", ClipboardList],
  ["rooms", "Room capacity", BedDouble],
  ["planner", "Ward planner", MoveRight],
  ["presentation", "Presentation", Presentation],
  ["doctors", "Doctors", Stethoscope],
  ["procedures", "Procedures", Activity],
  ["reports", "Reports", ChartNoAxesCombined],
] as const;

function SavedNotice() {
  const [message, setMessage] = useState("");
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const handler = (e: Event) => {
      setMessage((e as CustomEvent).detail);
      clearTimeout(timer);
      timer = setTimeout(() => setMessage(""), 3500);
    };
    window.addEventListener("saved", handler);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("saved", handler);
    };
  }, []);
  return (
    <div className="saved-notice" role="status" aria-live="polite">
      <AnimatePresence>
        {message && (
          <motion.div
            key={message}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
          >
            <CheckCircle2 size={19} />
            {message}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function Shell({
  onLogout,
  children,
}: {
  onLogout: () => void;
  children: React.ReactNode;
}) {
  const user = useUser(),
    pathname = usePathname();
  const [assistant, setAssistant] = useState(false),
    [mobile, setMobile] = useState(false);
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "k") {
        e.preventDefault();
        setAssistant((x) => !x);
      }
      if (e.key === "Escape") setMobile(false);
    };
    const launch = () => setAssistant(true);
    window.addEventListener("open-assistant", launch);
    window.addEventListener("keydown", key);
    return () => {
      window.removeEventListener("keydown", key);
      window.removeEventListener("open-assistant", launch);
    };
  }, []);
  useEffect(() => setMobile(false), [pathname]);
  const presentation = pathname === "/app/presentation";
  return (
    <div className={"app " + (presentation ? "presentation-mode" : "")}>
      {!presentation && (
        <a className="skip-link" href="#workspace-content">
          Skip to workspace
        </a>
      )}
      {mobile && (
        <button
          className="mobile-scrim"
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
        />
      )}
      <aside
        className={"sidebar " + (mobile ? "mobile-open" : "")}
        inert={presentation ? true : undefined}
        aria-hidden={presentation}
        hidden={presentation}
      >
        <Link to="/app/dashboard" className="brand">
          <span className="brandmark">
            <Activity size={21} />
          </span>
          medcore<span className="brand-dot">®</span>
        </Link>
        <WorkspaceSwitcher />
        <span className="nav-label">WORKSPACE</span>
        <LayoutGroup id="workspace-navigation">
          <nav>
            {nav.map(([url, label, Icon]) => (
              <NavLink to={"/app/" + url} key={url}>
                <Icon size={18} />
                {label}
              </NavLink>
            ))}
            {user.role === "ADMIN" && (
              <>
                <span className="nav-label admin-label">ADMINISTRATION</span>
                <NavLink to="/app/users">
                  <ShieldCheck size={18} />
                  Team access
                </NavLink>
                <NavLink to="/app/audit">
                  <History size={18} />
                  Audit history
                </NavLink>
              </>
            )}
            {user.accountRole === "ADMIN" && user.role !== "ADMIN" && (
              <p className="sidebar-note">
                Team access is in departments you administer. This department
                role is {user.role.replaceAll("_", " ").toLowerCase()}.
              </p>
            )}
          </nav>
        </LayoutGroup>
        <div className="sidebar-note">
          <span>More clarity.</span>
          <br />
          More room for care.
        </div>
        <button className="assistant-launch" onClick={() => setAssistant(true)}>
          <Sparkles size={20} />
          <span>
            Operations assistant<small>Find an answer. Prepare a task.</small>
          </span>
          <kbd>⌘ K</kbd>
        </button>
        <div className="profile">
          <div className="avatar">
            {user.username.substring(0, 2).toUpperCase()}
          </div>
          <div>
            {user.username}
            <small>
              {(user.departmentRole || user.role)
                .replaceAll("_", " ")
                .toLowerCase()}
              {user.accountRole && user.accountRole !== user.role
                ? ` · account ${user.accountRole.replaceAll("_", " ").toLowerCase()}`
                : ""}
            </small>
          </div>
          <button className="icon" aria-label="Sign out" onClick={onLogout}>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header
          className="topbar"
          inert={presentation ? true : undefined}
          aria-hidden={presentation}
          hidden={presentation}
        >
          <div>
            <button
              className="icon mobile-menu"
              aria-label="Open navigation"
              aria-expanded={mobile}
              onClick={() => setMobile(!mobile)}
            >
              <Menu />
            </button>
            <span>WORKSPACE</span>
            <span className="slash">/</span>
            <strong>
              {nav.find((n) => pathname.includes(n[0]))?.[1] ||
                "Administration"}
            </strong>
          </div>
          <div className="top-right">
            <LiveOperations />
            <NotificationCenter />
            <MotionToggle />
            <ThemeToggle />
            <span className="top-date">
              {new Date().toLocaleDateString("en-GB", {
                day: "numeric",
                month: "long",
                year: "numeric",
              })}
            </span>
            <button className="command" onClick={() => setAssistant(true)}>
              <Search size={16} />
              Search or ask<kbd>⌘ K</kbd>
            </button>
          </div>
        </header>
        <main id="workspace-content" tabIndex={-1}>
          <DemoReset user={user} />
          <SceneTransition scene={pathname}>{children}</SceneTransition>
        </main>
        <footer>
          <span>
            <span className="live-dot" />
            MEDCORE · OPERATIONS
          </span>
          <span>Every action, accounted for.</span>
        </footer>
      </div>
      <SavedNotice />
      <IdleTimeout />
      {pathname !== "/app/presentation" && (
        <MobileNavigation onAssistant={() => setAssistant(true)} />
      )}
      {assistant && <Assistant onClose={() => setAssistant(false)} />}
    </div>
  );
}
