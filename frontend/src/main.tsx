"use client";
import React, { useEffect, useState, useCallback } from "react";
import NextLink from "next/link";
import { usePathname } from "next/navigation";
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
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
  ArrowRight,
  LogOut,
  MoveRight,
  CheckCircle2,
  Sparkles,
  Menu,
  History,
  Presentation,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api, bindAccount, login, logout, type User } from "./api";
import { Auth, Link, useUser, ErrorBox } from "./components/workspace";
import { WorkspaceSwitcher } from "./components/WorkspaceSwitcher";
import { DemoAccess, DemoReset, WakeScreen } from "./features/demo/DemoAccess";
import {
  Registration,
  EmailVerification,
  ResendConfirmation,
} from "./features/auth/Registration";
import { PatientPortal } from "./features/patients/PatientPortal";
import { Assistant } from "./features/assistant/Assistant";
import { NotificationCenter } from "./components/NotificationCenter";
import { LiveOperations } from "./components/LiveOperations";
import { MobileNavigation } from "./components/MobileNavigation";
import { IdleTimeout } from "./components/IdleTimeout";
import {
  LoginScene,
  ThemeToggle,
  CinematicProvider,
  MotionToggle,
  SceneTransition,
  useCinematicMotion,
} from "./cinematic";

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
function App({ children }: { children: React.ReactNode }) {
  const qc = useQueryClient();
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true);
  const [awake, setAwake] = useState(false);
  const ready = useCallback(() => setAwake(true), []);
  useEffect(() => {
    if (!awake) return;
    api<User>("/auth/me")
      .then((next) => {
        bindAccount(next.id);
        setUser(next);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
    const expired = () => {
      bindAccount(null);
      setUser(null);
      qc.clear();
    };
    window.addEventListener("session-expired", expired);
    const switched = () => {
      api<User>("/auth/me")
        .then((next) => {
          bindAccount(next.id);
          setUser(next);
          void qc.invalidateQueries();
        })
        .catch(() => {});
    };
    window.addEventListener("workspace-changed", switched);
    return () => {
      window.removeEventListener("session-expired", expired);
      window.removeEventListener("workspace-changed", switched);
    };
  }, [awake]);
  const signOut = async () => {
    try {
      await logout();
    } finally {
      setUser(null);
      qc.clear();
    }
  };
  if (!awake) return <WakeScreen onReady={ready} />;
  if (loading)
    return (
      <div className="boot">
        <Activity />
        <p>Connecting to department…</p>
      </div>
    );
  if (user?.role === "PATIENT")
    return <PatientPortal user={user} onLogout={signOut} />;
  return user ? (
    <Auth.Provider value={user}>
      <Shell
        children={children}
        onLogout={async () => {
          try {
            await logout();
          } finally {
            setUser(null);
            qc.clear();
          }
        }}
      />
    </Auth.Provider>
  ) : (
    <Login onLogin={setUser} />
  );
}
function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const [signup, setSignup] = useState(false),
    [verification, setVerification] = useState("");
  useEffect(() => {
    const value = new URLSearchParams(window.location.hash.slice(1)).get(
      "verify",
    );
    if (value) setVerification(value);
  }, []);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(
      z.object({ username: z.string().min(1), password: z.string().min(1) }),
    ),
  });
  if (verification)
    return (
      <LoginScene>
        <EmailVerification
          token={verification}
          onBack={() => {
            setVerification("");
            window.history.replaceState(null, "", window.location.pathname);
          }}
        />
      </LoginScene>
    );
  if (signup)
    return (
      <LoginScene>
        <Registration onBack={() => setSignup(false)} />
      </LoginScene>
    );
  return (
    <LoginScene>
      <motion.form
        className="login-form"
        onSubmit={handleSubmit(async (data) => {
          setBusy(true);
          setError(null);
          try {
            onLogin(await login(data.username, data.password));
          } catch (e) {
            setError(e as Error);
          } finally {
            setBusy(false);
          }
        })}
      >
        <div className="access-icon">
          <ShieldCheck size={24} strokeWidth={1.4} />
        </div>
        <h2>Welcome back.</h2>
        <p className="form-intro">Sign in to your department.</p>
        <label>
          Username
          <input
            autoComplete="username"
            placeholder="Enter your username"
            aria-invalid={!!errors.username}
            {...register("username")}
          />
        </label>
        {errors.username && (
          <small className="invalid">Enter your username.</small>
        )}
        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            aria-invalid={!!errors.password}
            {...register("password")}
          />
        </label>
        {errors.password && (
          <small className="invalid">Enter your password.</small>
        )}
        <ErrorBox error={error} />
        <motion.button
          className="primary sign-in-button"
          disabled={busy}
          whileTap={{ scale: 0.98 }}
        >
          {busy ? "Verifying identity…" : "Sign in"}
          <span className="button-icon">
            <ArrowRight size={18} />
          </span>
        </motion.button>
        <p className="login-note">
          <ShieldCheck size={16} /> Access is restricted to authorized staff.
        </p>
        <button
          type="button"
          className="text-button"
          onClick={() => setSignup(true)}
        >
          Create a patient account
        </button>
        <ResendConfirmation />
        <DemoAccess onLogin={onLogin} />
      </motion.form>
    </LoginScene>
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
function Shell({
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
              {(user.departmentRole || user.role).replaceAll("_", " ").toLowerCase()}
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

export default function MedcoreApp({
  children,
}: {
  children: React.ReactNode;
}) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 15000 } },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <CinematicProvider>
        <App>{children}</App>
      </CinematicProvider>
    </QueryClientProvider>
  );
}
