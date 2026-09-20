"use client";

import React, {
  useEffect,
  useState,
  useRef,
  createContext,
  useContext,
  useCallback,
} from "react";
import NextLink from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { motion, AnimatePresence } from "motion/react";
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
  Plus,
  ArrowUpRight,
  ArrowRight,
  Check,
  Command,
  LogOut,
  X,
  MoveRight,
  CheckCircle2,
  Clock,
  AlertCircle,
  Download,
  Sparkles,
  Menu,
  History,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api, login, logout, fullName, money, date, Row, User } from "./api";
import { aiResponse } from "./ai-contract";
import { defaultProcedureTime } from "./workflow-time";
import { WardPlanner } from "./features/planner/WardPlanner";
import { OperationsOverview } from "./features/dashboard/OperationsOverview";
import { DemoAccess, DemoReset, WakeScreen } from "./features/demo/DemoAccess";
import { Registration, EmailVerification, ResendConfirmation } from "./features/auth/Registration";
import { PatientPortal } from "./features/patients/PatientPortal";
import { CommandResults } from "./features/assistant/CommandResults";
import { ProposalPreview } from "./features/assistant/ProposalPreview";
import { ProcedureCharts, CapacityChart } from "./features/reports/ReportCharts";
import { NotificationCenter } from "./components/NotificationCenter";
import { useUrlState } from "./components/useUrlState";
import { DataTable } from "./components/data-table/DataTable";
import { LoadingState } from "./components/LoadingState";
import {
  LoginScene,
  Reveal,
  ThemeToggle,
  MagneticButton,
  OverviewHero,
  CinematicProvider,
  MotionToggle,
  SceneTransition,
} from "./cinematic";

function Link({
  to,
  children,
  className,
}: {
  to: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <NextLink href={to} className={className}>
      {children}
    </NextLink>
  );
}

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const pathname = usePathname();
  const active = pathname === to || pathname.startsWith(to + "/");
  return (
    <NextLink
      href={to}
      className={active ? "active" : undefined}
      aria-current={active ? "page" : undefined}
    >
      <span className="nav-selection" aria-hidden="true" />
      {children}
    </NextLink>
  );
}
const qc = new QueryClient({
  defaultOptions: { queries: { retry: false, staleTime: 15000 } },
});
const Auth = createContext<User>(null!);
const useUser = () => useContext(Auth);
const useData = (key: string, path = key) =>
  useQuery<Row>({ queryKey: [key], queryFn: () => api(path) });
function ErrorBox({ error }: { error: any }) {
  return error ? (
    <div className="error" role="alert">
      <AlertCircle size={17} />
      {error.message || String(error)}
    </div>
  ) : null;
}
function Empty({ text = "No records found." }: { text?: string }) {
  return (
    <div className="empty">
      <ClipboardList size={26} />
      <p>{text}</p>
    </div>
  );
}
function Status({ value }: { value: string }) {
  return (
    <span
      className={
        "status " + (value === "ACTIVE" || value === "EXECUTED" ? "green" : "")
      }
    >
      {value.replaceAll("_", " ")}
    </span>
  );
}
function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-title">
        <h2>{title}</h2>
        <button aria-label="Close dialog" className="icon" onClick={onClose}>
          <X />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function App() {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true);
  const [awake,setAwake] = useState(false);
  const ready = useCallback(() => setAwake(true),[]);
  useEffect(() => {
    if (!awake) return;
    api<User>("/auth/me")
      .then(setUser)
      .catch(() => {})
      .finally(() => setLoading(false));
    const expired = () => {
      setUser(null);
      qc.clear();
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, [awake]);
  const signOut = async () => {try {await logout();} finally {setUser(null);qc.clear();}};
  if (!awake) return <WakeScreen onReady={ready}/>;
  if (loading)
    return (
      <div className="boot">
        <Activity />
        <p>Connecting to department…</p>
      </div>
    );
  if (user?.role === "PATIENT") return <PatientPortal user={user} onLogout={signOut}/>;
  return user ? (
    <Auth.Provider value={user}>
      <Shell
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
  const [signup,setSignup] = useState(false), [verification,setVerification] = useState("");
  useEffect(() => {const value=new URLSearchParams(window.location.hash.slice(1)).get("verify");if(value)setVerification(value);},[]);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(
      z.object({ username: z.string().min(1), password: z.string().min(1) }),
    ),
  });
  if (verification) return <LoginScene><EmailVerification token={verification} onBack={() => {setVerification("");window.history.replaceState(null,"",window.location.pathname);}}/></LoginScene>;
  if (signup) return <LoginScene><Registration onBack={() => setSignup(false)}/></LoginScene>;
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
        <button type="button" className="text-button" onClick={() => setSignup(true)}>Create a patient account</button>
        <ResendConfirmation/>
        <DemoAccess onLogin={onLogin}/>
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
  ["presentation", "Presentation", LayoutDashboard],
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
function Shell({ onLogout }: { onLogout: () => void }) {
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
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, []);
  useEffect(() => setMobile(false), [pathname]);
  return (
    <div className={"app " + (pathname === "/app/presentation" ? "presentation-mode" : "")}>
      <a className="skip-link" href="#workspace-content">
        Skip to workspace
      </a>
      {mobile && (
        <button
          className="mobile-scrim"
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
        />
      )}
      <aside className={"sidebar " + (mobile ? "mobile-open" : "")}>
        <Link to="/app/dashboard" className="brand">
          <span className="brandmark">
            <Activity size={21} />
          </span>
          medcore<span className="brand-dot">®</span>
        </Link>
        <div className="department">
          <BedDouble size={19} strokeWidth={1.5} />
          <div>
            Hospital department<small>Operations workspace</small>
          </div>
        </div>
        <span className="nav-label">WORKSPACE</span>
        <nav>
          {nav.map(([url, label, Icon]) => (
            <NavLink to={"/app/" + url} key={url}>
              <Icon size={18} />
              {label}
              <span className="nav-active-dot" />
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
        </nav>
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
            <small>{user.role.replaceAll("_", " ").toLowerCase()}</small>
          </div>
          <button className="icon" aria-label="Sign out" onClick={onLogout}>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
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
            <NotificationCenter/>
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
          <DemoReset user={user}/>
          <SceneTransition scene={pathname}>
            <RouteView
              pathname={pathname}
              user={user}
              onAssistant={() => setAssistant(true)}
            />
          </SceneTransition>
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
      {pathname !== "/app/presentation" && <button className="mobile-assistant" aria-label="Ask operations assistant" onClick={() => setAssistant(true)}><Sparkles size={20}/>Ask assistant</button>}
      {assistant && <Assistant onClose={() => setAssistant(false)} />}
    </div>
  );
}

function RouteView({
  pathname,
  user,
  onAssistant,
}: {
  pathname: string;
  user: User;
  onAssistant: () => void;
}) {
  const router = useRouter();
  const patientId = pathname.match(/^\/app\/patients\/(\d+)$/)?.[1];

  useEffect(() => {
    const known =
      pathname === "/app/dashboard" ||
      pathname === "/app/patients" ||
      Boolean(patientId) ||
      [
        "/app/admissions",
        "/app/rooms",
        "/app/doctors",
        "/app/procedures",
        "/app/users",
        "/app/reports",
        "/app/audit",
        "/app/planner",
        "/app/presentation",
      ].includes(pathname);
    if (!known) router.replace("/app/dashboard");
  }, [pathname, patientId, router]);

  if (pathname === "/app/dashboard")
    return <Dashboard onAssistant={onAssistant} />;
  if (pathname === "/app/patients") return <Patients />;
  if (patientId) return <PatientDetail id={patientId} />;
  if (pathname === "/app/admissions") return <Admissions />;
  if (pathname === "/app/planner") return <WardPlanner user={user}/>;
  if (pathname === "/app/presentation") return <><div className="presentation-toolbar"><div><h1>Medcore · Live ward</h1><p>Current operations · {user.role === "DOCTOR" ? "Assigned patient scope" : "Department"}</p></div><div className="actions"><button className="secondary" onClick={() => {if(document.fullscreenElement)document.exitFullscreen();else document.documentElement.requestFullscreen().catch(() => {});}}>Toggle full screen</button><Link to="/app/dashboard">Exit presentation</Link></div></div><OperationsOverview presentation/></>;
  if (pathname === "/app/rooms") return <Rooms />;
  if (pathname === "/app/doctors") return <Catalogue kind="doctors" />;
  if (pathname === "/app/procedures") return <Catalogue kind="procedures" />;
  if (pathname === "/app/users")
    return user.role === "ADMIN" ? <Catalogue kind="users" /> : <Denied />;
  if (pathname === "/app/reports") return <Reports />;
  if (pathname === "/app/audit")
    return user.role === "ADMIN" ? <Audit /> : <Denied />;
  return <div className="skeleton">Opening workspace…</div>;
}
function Denied() {
  return (
    <div className="empty">
      <ShieldCheck size={38} />
      <h2>Access restricted</h2>
      <p>Your role does not have access to this view.</p>
      <Link to="/app/dashboard">Return to overview</Link>
    </div>
  );
}
function Title({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}
function Dashboard({ onAssistant }: { onAssistant: () => void }) {
  const { data: d, error, isLoading } = useData("/reports/dashboard");
  const { data: rooms } = useData("/rooms");
  const { data: admissions } = useData("/admissions");
  return (
    <>
      <OverviewHero>
        <Title
          eyebrow="DEPARTMENT OVERVIEW"
          title="A clear picture of today."
          description="The people, capacity and activity that keep your department moving."
        >
          <span className="live-label">
            <span className="live-dot" />
            Live operations
          </span>
        </Title>
      </OverviewHero>
      <OperationsOverview/>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading department state…</div>
      ) : (
        d && (
          <div className="metrics">
            {[
              [
                "Active admissions",
                d.activeAdmissions,
                "Assigned scope",
                Users,
              ],
              [
                "Beds occupied",
                `${d.occupiedBeds} / ${d.totalBeds}`,
                `${d.availableBeds} beds available`,
                BedDouble,
              ],
              [
                "Active doctors",
                d.activeDoctors,
                "Department team",
                Stethoscope,
              ],
              [
                "Procedures today",
                d.proceduresToday,
                "Recorded in UTC",
                Activity,
              ],
            ].map(([label, value, note, Icon]: any, index) => (
              <Reveal className="metric" key={label} delay={index * 0.08}>
                <div>
                  <span>{label}</span>
                  <Icon size={19} />
                </div>
                <strong>{value}</strong>
                <small>{note}</small>
              </Reveal>
            ))}
          </div>
        )
      )}
      <Reveal>
        <section className="brief">
          <div className="brief-icon">
            <Sparkles size={22} />
          </div>
          <div>
            <span className="eyebrow">OPERATIONS BRIEF</span>
            <p>
              {isLoading
                ? "Preparing your operational snapshot…"
                : d
                  ? `${d.availableBeds} beds are available across the department. ${d.activeAdmissions} active admissions are visible in your scope. ${d.proceduresToday} procedures have been recorded today.`
                  : "Department totals could not be loaded. Refresh the page to try again."}
            </p>
            <small>Live department totals</small>
          </div>
          <MagneticButton className="secondary" onClick={onAssistant}>
            Explore with assistant
          </MagneticButton>
        </section>
      </Reveal>
      <div className="overview-grid">
        <section className="panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">SPACE FOR CARE</span>
              <h2>Room capacity</h2>
            </div>
            <Link to="/app/rooms">
              View all
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="mini-rooms">
            {Array.isArray(rooms) &&
              rooms
                .slice(0, 8)
                .map((r: Row) => <RoomCard key={r.id} room={r} compact />)}
          </div>
          <div className="legend">
            <span>
              <i />
              Occupied
            </span>
            <span>
              <i />
              Available
            </span>
          </div>
        </section>
        <section className="panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">IN YOUR DEPARTMENT</span>
              <h2>Recent admissions</h2>
            </div>
            <Link to="/app/admissions">
              View all
              <ArrowUpRight size={16} />
            </Link>
          </div>
          <div className="recent-list">
            {Array.isArray(admissions) &&
              admissions.slice(0, 5).map((v: Row) => (
                <Link key={v.admission.id} to={"/app/patients/" + v.patient.id}>
                  <div className="avatar">
                    {v.patient.firstName[0]}
                    {v.patient.lastName[0]}
                  </div>
                  <div>
                    <strong>{fullName(v.patient)}</strong>
                    <small>
                      {v.patient.patientIdentifier} ·{" "}
                      {date(v.admission.admissionDateTime)}
                    </small>
                  </div>
                  <Status value={v.admission.status} />
                </Link>
              ))}
            {Array.isArray(admissions) && admissions.length === 0 && <Empty />}
          </div>
        </section>
      </div>
    </>
  );
}
function RoomCard({
  room: r,
  compact = false,
  onEdit,
}: {
  room: Row;
  compact?: boolean;
  onEdit?: () => void;
}) {
  return (
    <div className={"room-card " + (!r.availableBeds ? "full" : "")}>
      <div className="room-card-top">
        <span>
          ROOM <b>{r.roomNumber}</b>
        </span>
        {onEdit ? (
          <button className="text-button" onClick={onEdit}>
            Edit
          </button>
        ) : (
          <BedDouble size={16} />
        )}
      </div>
      <div className="beds">
        {Array.from({ length: Math.min(r.bedCount, 12) }, (_, i) => (
          <span key={i} className={i < r.occupiedBeds ? "occupied" : ""}>
            <BedDouble size={compact ? 19 : 24} />
          </span>
        ))}
        {r.bedCount > 12 && <small>+{r.bedCount - 12}</small>}
      </div>
      <div className="room-card-bottom">
        <span className={r.availableBeds ? "available" : "muted"}>
          {r.active
            ? r.availableBeds
              ? `${r.availableBeds} available`
              : "At capacity"
            : "Inactive"}
        </span>
        <small>
          {r.occupiedBeds} / {r.bedCount}
        </small>
      </div>
    </div>
  );
}
function Patients() {
  const user = useUser();
  const [search, setSearch] = useUrlState("q");
  const [edit, setEdit] = useState<Row | null>(null);
  const { data, error, isLoading } = useData(
    "/patients?q=" + encodeURIComponent(search),
  );
  return (
    <>
      <Title
        eyebrow="PATIENT DIRECTORY"
        title="People at the center."
        description="Search patient records, manage details and open an operational history."
      >
        {user.role !== "DOCTOR" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            New patient
          </button>
        )}
      </Title>
      <div className="toolbar">
        <Search size={18} />
        <input
          aria-label="Search patients"
          placeholder="Search by name or patient ID"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <span>{Array.isArray(data) ? data.length : 0} records</span>
      </div>
      <ErrorBox error={error} />
      <div className="panel table-panel">
        <DataTable<import("./api/contracts").Patient> rows={Array.isArray(data) ? data : []} rowKey={p=>p.id} columns={[
          {key:"name",label:"Patient",value:fullName,render:p=><Link className="person-link" to={"/app/patients/"+p.id}><span className="avatar">{p.firstName[0]}{p.lastName[0]}</span>{fullName(p)}</Link>},
          {key:"identifier",label:"Patient ID",value:p=>p.patientIdentifier,render:p=>p.patientIdentifier},
          {key:"birth",label:"Date of birth",value:p=>p.dateOfBirth,render:p=>p.dateOfBirth},
          {key:"phone",label:"Phone",render:p=>p.phoneNumber || "Not recorded"},
          {key:"open",label:"Record",render:p=><Link to={"/app/patients/"+p.id} className="text-button">Open dossier<ArrowUpRight size={16}/></Link>}
        ]}/>
        {isLoading ? (
          <LoadingState label="Loading patients"/>
        ) : (
          Array.isArray(data) && data.length === 0 && <Empty />
        )}
      </div>
      {edit && (
        <EntityForm
          kind="patients"
          record={edit}
          onClose={() => setEdit(null)}
        />
      )}
    </>
  );
}
function Rooms() {
  const [edit, setEdit] = useState<Row | null>(null),
    [free, setFree] = useState(false);
  const { data, error, isLoading } = useData("/rooms");
  const user = useUser();
  return (
    <>
      <Title
        eyebrow="CAPACITY MATRIX"
        title="The right space, in view."
        description="Live bed availability. Capacity is checked again at admission and transfer."
      >
        {user.role === "ADMIN" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            Add room
          </button>
        )}
      </Title>
      <div className="toolbar">
        <BedDouble size={19} />
        <span>Department rooms</span>
        <label className="inline-check">
          <input
            type="checkbox"
            checked={free}
            onChange={(e) => setFree(e.target.checked)}
          />
          Available only
        </label>
      </div>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Loading capacity…</div>
      ) : (
        <motion.div layout className="room-grid">
          {Array.isArray(data) &&
            data
              .filter((r: Row) => !free || r.availableBeds > 0)
              .map((r: Row) => (
                <motion.div layout key={r.id}>
                  <RoomCard
                    room={r}
                    onEdit={
                      user.role === "ADMIN" ? () => setEdit(r) : undefined
                    }
                  />
                </motion.div>
              ))}
        </motion.div>
      )}
      {edit && (
        <EntityForm kind="rooms" record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
function Admissions() {
  const { data, error, isLoading } = useData("/admissions");
  const [active, setActive] = useState(true);
  return (
    <>
      <Title
        eyebrow="HOSPITALIZATION"
        title="Every stay, connected."
        description="Follow admissions from placement through transfer and discharge."
      />
      <div className="toolbar">
        <ClipboardList size={18} />
        <span>Admission register</span>
        <label className="inline-check">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
          />
          Active only
        </label>
      </div>
      <ErrorBox error={error} />
      <div className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th scope="col">Patient</th>
              <th scope="col">Admission</th>
              <th scope="col">Attending doctor</th>
              <th scope="col">Room</th>
              <th scope="col">Status</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data
                .filter((v: Row) => !active || v.admission.status === "ACTIVE")
                .map((v: Row) => (
                  <tr key={v.admission.id}>
                    <td>
                      <Link to={"/app/patients/" + v.patient.id}>
                        {fullName(v.patient)}
                        <ArrowUpRight size={14} />
                      </Link>
                    </td>
                    <td>
                      <span className="mono">
                        {v.admission.admissionNumber}
                      </span>
                      <small>{date(v.admission.admissionDateTime)}</small>
                    </td>
                    <td>Dr. {fullName(v.doctor)}</td>
                    <td>
                      {v.rooms.find((r: Row) => !r.assignment.releasedAt)?.room
                        .roomNumber || "Not assigned"}
                    </td>
                    <td>
                      <Status value={v.admission.status} />
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading admissions…</div>}
        {Array.isArray(data) &&
          data.filter((v: Row) => !active || v.admission.status === "ACTIVE")
            .length === 0 && <Empty />}
      </div>
    </>
  );
}
function PatientDetail({ id }: { id: string }) {
  const { data, error, isLoading } = useData("/patients/" + id);
  const [edit, setEdit] = useState(false),
    [flow, setFlow] = useState<string | null>(null);
  const user = useUser();
  if (isLoading)
    return <div className="skeleton">Opening patient dossier…</div>;
  if (error) return <ErrorBox error={error} />;
  if (!data) return null;
  const p = data.patient,
    active = data.admissions.find((v: Row) => v.admission.status === "ACTIVE");
  return (
    <>
      <Link className="back" to="/app/patients">
        ← Patient directory
      </Link>
      <Title
        eyebrow={"PATIENT DOSSIER / " + p.patientIdentifier}
        title={fullName(p)}
        description={
          "Born " +
          p.dateOfBirth +
          " · " +
          (p.phoneNumber || "No phone recorded")
        }
      >
        {user.role !== "DOCTOR" && (
          <div className="actions">
            <button className="secondary" onClick={() => setEdit(true)}>
              Edit details
            </button>
            {!active && (
              <button className="primary" onClick={() => setFlow("admit")}>
                <Plus size={17} />
                Admit patient
              </button>
            )}
          </div>
        )}
      </Title>
      <div className="detail-banner">
        <span>
          <small>PATIENT ID</small>
          {p.patientIdentifier}
        </span>
        <span>
          <small>ADDRESS</small>
          {p.address || "Not recorded"}
        </span>
        <span>
          <small>RECORDED STAYS</small>
          {data.admissions.length}
        </span>
        <span>
          <small>CURRENT STATUS</small>
          <Status value={active ? "ACTIVE" : "NOT ADMITTED"} />
        </span>
      </div>
      {active && (
        <section className="active-stay">
          <div>
            <span className="eyebrow">CURRENT ADMISSION</span>
            <h2>
              Room{" "}
              {
                active.rooms.find((r: Row) => !r.assignment.releasedAt)?.room
                  .roomNumber
              }
            </h2>
            <p>
              Dr. {fullName(active.doctor)} · Since{" "}
              {date(active.admission.admissionDateTime)}
            </p>
          </div>
          <div className="actions">
            <button className="secondary" onClick={() => setFlow("procedure")}>
              Record procedure
            </button>
            {user.role !== "DOCTOR" && (
              <>
                <button className="secondary" onClick={() => setFlow("doctor")}>
                  Assign doctor
                </button>
                <button
                  className="secondary"
                  onClick={() => setFlow("transfer")}
                >
                  <MoveRight size={17} />
                  Transfer
                </button>
                <button
                  className="primary"
                  onClick={() => setFlow("discharge")}
                >
                  Discharge
                  <ArrowRight size={17} />
                </button>
              </>
            )}
          </div>
        </section>
      )}
      <div className="section-heading">
        <div>
          <span className="eyebrow">OPERATIONAL HISTORY</span>
          <h2>Admission timeline</h2>
        </div>
      </div>
      {data.admissions.length === 0 ? (
        <Empty text="No hospitalizations recorded for this patient." />
      ) : (
        data.admissions.map((v: Row) => (
          <section className="panel stay" key={v.admission.id}>
            <div className="section-heading">
              <div>
                <h3>{v.admission.admissionNumber}</h3>
                <p>
                  {date(v.admission.admissionDateTime)} →{" "}
                  {v.admission.dischargeDateTime
                    ? date(v.admission.dischargeDateTime)
                    : "Present"}
                </p>
              </div>
              <Status value={v.admission.status} />
            </div>
            <div className="stay-body">
              <div>
                <span className="eyebrow">ROOM MOVEMENTS</span>
                <ol className="timeline">
                  {v.rooms.map((r: Row) => (
                    <li key={r.assignment.id}>
                      <span>Room {r.room.roomNumber}</span>
                      <small>
                        {date(r.assignment.assignedAt)}
                        {r.assignment.releasedAt
                          ? " → " + date(r.assignment.releasedAt)
                          : " · Current placement"}
                      </small>
                      <p>{r.assignment.reason}</p>
                    </li>
                  ))}
                </ol>
              </div>
              <div>
                <div className="section-heading">
                  <span className="eyebrow">PERFORMED PROCEDURES</span>
                  <strong>{money(v.totalCost)}</strong>
                </div>
                {v.procedures.length === 0 ? (
                  <p className="muted">No procedures recorded.</p>
                ) : (
                  v.procedures.map((r: Row) => (
                    <div className="procedure-record" key={r.record.id}>
                      <strong>{r.procedure.procedureName}</strong>
                      <span>{money(r.record.priceAtExecution)}</span>
                      <small>
                        {date(r.record.performedAt)} · Dr. {fullName(r.doctor)}
                      </small>
                      {r.record.note && <p>{r.record.note}</p>}
                    </div>
                  ))
                )}
              </div>
            </div>
          </section>
        ))
      )}
      {edit && (
        <EntityForm kind="patients" record={p} onClose={() => setEdit(false)} />
      )}{" "}
      {flow && (
        <Workflow
          kind={flow}
          patient={p}
          active={active}
          onClose={() => setFlow(null)}
        />
      )}
    </>
  );
}
type Field = {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  options?: { value: string; label: string }[];
};
const configs: Record<
  string,
  { title: string; singular: string; description: string; fields: Field[] }
> = {
  patients: {
    title: "Patients",
    singular: "patient",
    description: "Patient demographics",
    fields: [
      { key: "patientIdentifier", label: "Patient ID", required: true },
      { key: "firstName", label: "First name", required: true },
      { key: "lastName", label: "Last name", required: true },
      {
        key: "dateOfBirth",
        label: "Date of birth",
        type: "date",
        required: true,
      },
      { key: "phoneNumber", label: "Phone number" },
      { key: "address", label: "Address" },
    ],
  },
  doctors: {
    title: "The department team.",
    singular: "doctor",
    description: "Maintain the physician directory and active clinical team.",
    fields: [
      { key: "doctorIdentifier", label: "Doctor ID", required: true },
      { key: "firstName", label: "First name", required: true },
      { key: "lastName", label: "Last name", required: true },
      { key: "specialty", label: "Specialty", required: true },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  rooms: {
    title: "Rooms",
    singular: "room",
    description: "Room configuration",
    fields: [
      { key: "roomNumber", label: "Room number", required: true },
      { key: "bedCount", label: "Bed count", type: "number", required: true },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  procedures: {
    title: "A consistent catalogue.",
    singular: "procedure",
    description:
      "Department procedures and current pricing. Recorded costs retain their original value.",
    fields: [
      { key: "procedureCode", label: "Procedure code", required: true },
      { key: "procedureName", label: "Procedure name", required: true },
      {
        key: "currentCost",
        label: "Cost (EUR)",
        type: "number",
        required: true,
      },
      { key: "active", label: "Active", type: "checkbox" },
    ],
  },
  users: {
    title: "Access with accountability.",
    singular: "user",
    description:
      "Manage staff accounts, physician links and role-based permissions.",
    fields: [
      { key: "username", label: "Username", required: true },
      { key: "password", label: "Password (12+ characters)", type: "password" },
      {
        key: "role",
        label: "Role",
        type: "select",
        required: true,
        options: [
          { value: "MEDICAL_STAFF", label: "Medical staff" },
          { value: "DOCTOR", label: "Doctor" },
          { value: "ADMIN", label: "Administrator" },
          { value: "PATIENT", label: "Patient (registered account)" },
        ],
      },
      { key: "doctorId", label: "Linked doctor", type: "select" },
      { key: "enabled", label: "Enabled", type: "checkbox" },
    ],
  },
};
function EntityForm({
  kind,
  record,
  onClose,
}: {
  kind: string;
  record: Row;
  onClose: () => void;
}) {
  const cfg = configs[kind],
    client = useQueryClient();
  const [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const { data: doctors } = useData("/doctors");
  const defaults: Row = {
    active: true,
    enabled: true,
    role: "MEDICAL_STAFF",
    ...record,
    password: "",
  };
  const shape: Record<string, z.ZodTypeAny> = {};
  cfg.fields.forEach(
    (f) =>
      (shape[f.key] =
        f.type === "checkbox"
          ? z.boolean()
          : f.type === "number"
            ? f.key === "bedCount"
              ? z.coerce
                  .number()
                  .int("Use a whole number of beds")
                  .min(1)
                  .max(100)
              : z.coerce.number().min(0)
            : f.required
              ? z.string().trim().min(1, "Required")
              : z.string().optional()),
  );
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Row>({
    defaultValues: defaults,
    resolver: zodResolver(z.object(shape)),
  });
  return (
    <Modal
      title={`${record.id ? "Edit" : "New"} ${cfg.singular}`}
      onClose={onClose}
    >
      <form
        className="form-grid"
        onSubmit={handleSubmit(async (values) => {
          setBusy(true);
          setError(null);
          try {
            const body: Row = { ...values, version: record.version ?? null };
            if (kind === "users") {
              body.doctorId =
                body.role === "DOCTOR" && body.doctorId
                  ? Number(body.doctorId)
                  : null;
              if (record.id && !body.password) delete body.password;
            }
            await api(
              "/" + kind + (record.id ? "/" + record.id : ""),
              record.id ? "PUT" : "POST",
              body,
            );
            await client.invalidateQueries();
            onClose();
          } catch (e) {
            setError(e as Error);
          } finally {
            setBusy(false);
          }
        })}
      >
        {cfg.fields.map((f) => (
          <label
            key={f.key}
            className={f.type === "checkbox" ? "inline-check form-full" : ""}
          >
            {f.label}
            {f.type === "select" ? (
              <select {...register(f.key)}>
                <option value="">Select…</option>
                {(f.key === "doctorId"
                  ? Array.isArray(doctors)
                    ? doctors
                        .filter((d: Row) => d.active)
                        .map((d: Row) => ({
                          value: String(d.id),
                          label: fullName(d),
                        }))
                    : []
                  : f.options || []
                ).map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type={f.type || "text"}
                {...register(f.key)}
                readOnly={
                  kind === "users" && f.key === "username" && Boolean(record.id)
                }
                autoComplete={f.type === "password" ? "new-password" : "off"}
                max={
                  f.type === "date"
                    ? new Date().toISOString().slice(0, 10)
                    : undefined
                }
                step={
                  f.type === "number"
                    ? f.key === "bedCount"
                      ? "1"
                      : "0.01"
                    : undefined
                }
              />
            )}{" "}
            {errors[f.key] && (
              <small className="invalid">
                {String(errors[f.key]?.message)}
              </small>
            )}
          </label>
        ))}
        <div className="form-full">
          <ErrorBox error={error} />
        </div>
        <div className="modal-actions form-full">
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save " + cfg.singular}
            <Check size={17} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
function Catalogue({ kind }: { kind: string }) {
  const cfg = configs[kind],
    { data, error, isLoading } = useData("/" + kind);
  const [edit, setEdit] = useState<Row | null>(null);
  const user = useUser();
  const columns =
    kind === "doctors"
      ? ["Doctor", "Specialty", "Status"]
      : kind === "procedures"
        ? ["Procedure", "Code", "Current cost", "Status"]
        : ["Username", "Role", "Doctor ID", "Status"];
  return (
    <>
      <Title
        eyebrow={kind === "users" ? "TEAM ACCESS" : "DEPARTMENT DIRECTORY"}
        title={cfg.title}
        description={cfg.description}
      >
        {user.role === "ADMIN" && (
          <button className="primary" onClick={() => setEdit({})}>
            <Plus size={18} />
            Add {cfg.singular}
          </button>
        )}
      </Title>
      <ErrorBox error={error} />
      <section className="panel table-panel">
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th scope="col" key={c}>
                  {c}
                </th>
              ))}
              {user.role === "ADMIN" && <th />}
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data.map((r: Row) => (
                <tr key={r.id}>
                  {kind === "doctors" ? (
                    <>
                      <td>
                        <strong>Dr. {fullName(r)}</strong>
                        <small>{r.doctorIdentifier}</small>
                      </td>
                      <td>{r.specialty}</td>
                      <td>
                        <Status value={r.active ? "ACTIVE" : "INACTIVE"} />
                      </td>
                    </>
                  ) : kind === "procedures" ? (
                    <>
                      <td>
                        <strong>{r.procedureName}</strong>
                      </td>
                      <td className="mono">{r.procedureCode}</td>
                      <td>{money(r.currentCost)}</td>
                      <td>
                        <Status value={r.active ? "ACTIVE" : "INACTIVE"} />
                      </td>
                    </>
                  ) : (
                    <>
                      <td>{r.username}{r.email && <small className="muted">{r.email} · {r.emailVerified ? "Email verified" : "Awaiting verification"}</small>}{r.requestedRole === "DOCTOR" && r.role !== "DOCTOR" && <small className="status">Doctor access requested</small>}</td>
                      <td>{r.role.replaceAll("_", " ")}</td>
                      <td>{r.doctorId || "Not assigned"}</td>
                      <td>
                        <Status value={r.enabled ? "ACTIVE" : "DISABLED"} />
                      </td>
                    </>
                  )}
                  {user.role === "ADMIN" && (
                    <td>
                      <button
                        className="text-button"
                        onClick={() => setEdit(r)}
                      >
                        Edit
                        <ArrowUpRight size={15} />
                      </button>
                    </td>
                  )}
                </tr>
              ))}
          </tbody>
        </table>
        {isLoading ? (
          <div className="skeleton">Loading records…</div>
        ) : (
          Array.isArray(data) && !data.length && <Empty />
        )}
      </section>
      {edit && (
        <EntityForm kind={kind} record={edit} onClose={() => setEdit(null)} />
      )}
    </>
  );
}
function Workflow({
  kind,
  patient,
  active,
  onClose,
}: {
  kind: string;
  patient: Row;
  active?: Row;
  onClose: () => void;
}) {
  const user = useUser(),
    client = useQueryClient();
  const { data: doctors } = useData("/doctors"),
    { data: rooms } = useData("/rooms"),
    { data: procedures } = useData("/procedures");
  const [values, setValues] = useState<Row>({
      doctorId: active?.doctor.id || user.doctorId || "",
      roomId: "",
      medicalProcedureId: "",
      reason: "",
      note: "",
      performedAt: defaultProcedureTime(active?.admission.admissionDateTime),
    }),
    [review, setReview] = useState(kind === "discharge"),
    [error, setError] = useState<Error | null>(null),
    [busy, setBusy] = useState(false);
  const name = (
    {
      admit: "Admit patient",
      transfer: "Transfer patient",
      discharge: "Discharge patient",
      procedure: "Record procedure",
      doctor: "Assign attending doctor",
    } as Row
  )[kind];
  const update =
    (key: string) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >,
    ) =>
      setValues((v) => ({ ...v, [key]: e.target.value }));
  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      const admission = active?.admission;
      if (kind === "admit")
        await api("/admissions", "POST", {
          patientId: patient.id,
          doctorId: Number(values.doctorId),
          roomId: Number(values.roomId),
        });
      else if (kind === "transfer")
        await api(`/admissions/${admission.id}/transfer`, "POST", {
          roomId: Number(values.roomId),
          reason: values.reason,
          version: admission.version,
        });
      else if (kind === "discharge")
        await api(`/admissions/${admission.id}/discharge`, "POST", {
          version: admission.version,
        });
      else if (kind === "doctor")
        await api(`/admissions/${admission.id}/doctor`, "POST", {
          doctorId: Number(values.doctorId),
          version: admission.version,
        });
      else
        await api(`/admissions/${admission.id}/procedures`, "POST", {
          doctorId: Number(values.doctorId),
          medicalProcedureId: Number(values.medicalProcedureId),
          performedAt: new Date(values.performedAt).toISOString(),
          note: values.note,
        });
      await client.invalidateQueries();
      onClose();
    } catch (e) {
      setError(e as Error);
      await client.invalidateQueries();
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal title={name} onClose={onClose}>
      <div className="flow-patient">
        <span className="avatar">
          {patient.firstName[0]}
          {patient.lastName[0]}
        </span>
        <div>
          <strong>{fullName(patient)}</strong>
          <small>{patient.patientIdentifier}</small>
        </div>
      </div>
      {review ? (
        <>
          <div className="review">
            <span className="eyebrow">REVIEW & CONFIRM</span>
            <h3>
              {kind === "discharge"
                ? "Close this admission?"
                : "Confirm the details below."}
            </h3>
            <p>
              {kind === "discharge"
                ? "Discharge closes the active stay and releases the occupied bed. Confirm only after the discharge decision has been made by the responsible clinician."
                : "The system will recheck permissions, availability and the current record before saving."}
            </p>
            {values.roomId && (
              <p>
                Destination:{" "}
                <strong>
                  Room{" "}
                  {Array.isArray(rooms) &&
                    rooms.find((r: Row) => r.id === Number(values.roomId))
                      ?.roomNumber}
                </strong>
              </p>
            )}
            {values.doctorId && (
              <p>
                Doctor:{" "}
                <strong>
                  {Array.isArray(doctors) &&
                    fullName(
                      doctors.find(
                        (d: Row) => d.id === Number(values.doctorId),
                      ),
                    )}
                </strong>
              </p>
            )}
            {kind === "procedure" && (
              <p>
                Procedure:{" "}
                <strong>
                  {Array.isArray(procedures) &&
                    procedures.find(
                      (p: Row) => p.id === Number(values.medicalProcedureId),
                    )?.procedureName}
                </strong>
              </p>
            )}
            {kind === "discharge" && (
              <p>
                Recorded procedure total:{" "}
                <strong>{money(active?.totalCost)}</strong>
              </p>
            )}
          </div>
          <ErrorBox error={error} />
          <div className="modal-actions">
            <button
              className="secondary"
              onClick={() =>
                kind === "discharge" ? onClose() : setReview(false)
              }
              disabled={busy}
            >
              {kind === "discharge" ? "Cancel" : "Back"}
            </button>
            <button className="primary" onClick={submit} disabled={busy}>
              {busy
                ? "Saving…"
                : "Confirm " +
                  (
                    {
                      admit: "admission",
                      transfer: "transfer",
                      discharge: "discharge",
                      procedure: "procedure",
                      doctor: "assignment",
                    } as Row
                  )[kind]}
              <CheckCircle2 size={17} />
            </button>
          </div>
        </>
      ) : (
        <form
          className="workflow-form"
          onSubmit={(e) => {
            e.preventDefault();
            setReview(true);
          }}
        >
          {["admit", "doctor", "procedure"].includes(kind) && (
            <label>
              Attending / performing doctor
              <select
                aria-label="Attending / performing doctor"
                required
                value={values.doctorId}
                onChange={update("doctorId")}
              >
                <option value="">Select doctor…</option>
                {Array.isArray(doctors) &&
                  doctors
                    .filter(
                      (d: Row) =>
                        d.active &&
                        (user.role !== "DOCTOR" || d.id === user.doctorId),
                    )
                    .map((d: Row) => (
                      <option value={d.id} key={d.id}>
                        Dr. {fullName(d)}
                      </option>
                    ))}
              </select>
            </label>
          )}
          {["admit", "transfer"].includes(kind) && (
            <label>
              Destination room
              <select
                aria-label="Destination room"
                required
                value={values.roomId}
                onChange={update("roomId")}
              >
                <option value="">Select available room…</option>
                {Array.isArray(rooms) &&
                  rooms
                    .filter(
                      (r: Row) =>
                        r.active &&
                        r.availableBeds > 0 &&
                        r.id !== active?.assignment?.roomId,
                    )
                    .map((r: Row) => (
                      <option key={r.id} value={r.id}>
                        Room {r.roomNumber} · {r.availableBeds} free beds
                      </option>
                    ))}
              </select>
            </label>
          )}
          {kind === "transfer" && (
            <label>
              Reason for transfer
              <textarea
                required
                maxLength={500}
                value={values.reason}
                onChange={update("reason")}
              />
            </label>
          )}
          {kind === "procedure" && (
            <>
              <label>
                Procedure
                <select
                  aria-label="Procedure"
                  required
                  value={values.medicalProcedureId}
                  onChange={update("medicalProcedureId")}
                >
                  <option value="">Select procedure…</option>
                  {Array.isArray(procedures) &&
                    procedures
                      .filter((p: Row) => p.active)
                      .map((p: Row) => (
                        <option key={p.id} value={p.id}>
                          {p.procedureName} · {money(p.currentCost)}
                        </option>
                      ))}
                </select>
              </label>
              <label>
                Performed at
                <input
                  required
                  type="datetime-local"
                  step="0.001"
                  value={values.performedAt}
                  onChange={update("performedAt")}
                />
              </label>
              <label>
                Notes
                <textarea
                  maxLength={2000}
                  value={values.note}
                  onChange={update("note")}
                />
              </label>
            </>
          )}
          <div className="modal-actions">
            <button type="button" className="secondary" onClick={onClose}>
              Cancel
            </button>
            <button className="primary">
              Review details
              <ArrowRight size={17} />
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
function Reports() {
  const today = new Date().toISOString().slice(0, 10);
  const [from, setFrom] = useUrlState("from",today.slice(0, 8) + "01"),
    [to, setTo] = useUrlState("to",today),
    [patientId, setPatient] = useUrlState("patientId"),
    [doctorId, setDoctor] = useUrlState("doctorId"),
    [roomId, setRoom] = useUrlState("roomId"),
    [mode, setMode] = useUrlState("mode","procedures");
  const { data: patients } = useData("/patients"),
    { data: doctors } = useData("/doctors"),
    { data: rooms } = useData("/rooms");
  const params = new URLSearchParams({
    from,
    to,
    ...(patientId ? { patientId } : {}),
    ...(doctorId ? { doctorId } : {}),
  });
  const path =
    mode === "procedures"
      ? "/reports/procedures?" + params
      : mode === "capacity"
        ? "/reports/capacity"
        : "/reports/census?" +
          new URLSearchParams({
            ...(doctorId ? { doctorId } : {}),
            ...(roomId ? { roomId } : {}),
          });
  const { data, error, isLoading } = useData(path);
  return (
    <>
      <Title
        eyebrow="OPERATIONAL REPORTING"
        title="Decisions, grounded in data."
        description="Authoritative reports calculated from saved department records."
      />
      <div className="tabs">
        {["procedures", "census", "capacity"].map((m) => (
          <button
            className={mode === m ? "selected" : ""}
            onClick={() => setMode(m)}
            key={m}
          >
            {m === "census"
              ? "Hospitalized patients"
              : m === "capacity"
                ? "Bed capacity"
                : "Procedure activity"}
          </button>
        ))}
      </div>
      <div className="report-filters">
        {mode === "procedures" && (
          <>
            <label>
              From
              <input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label>
              To
              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
            <label>
              Patient
              <select
                value={patientId}
                onChange={(e) => setPatient(e.target.value)}
              >
                <option value="">All permitted patients</option>
                {Array.isArray(patients) &&
                  patients.map((p: Row) => (
                    <option value={p.id} key={p.id}>
                      {fullName(p)}
                    </option>
                  ))}
              </select>
            </label>
          </>
        )}
        {mode !== "capacity" && (
          <label>
            Doctor
            <select
              value={doctorId}
              onChange={(e) => setDoctor(e.target.value)}
            >
              <option value="">All doctors</option>
              {Array.isArray(doctors) &&
                doctors.map((d: Row) => (
                  <option value={d.id} key={d.id}>
                    {fullName(d)}
                  </option>
                ))}
            </select>
          </label>
        )}
        {mode === "census" && (
          <label>
            Room
            <select value={roomId} onChange={(e) => setRoom(e.target.value)}>
              <option value="">All rooms</option>
              {Array.isArray(rooms) &&
                rooms.map((r: Row) => (
                  <option value={r.id} key={r.id}>
                    {r.roomNumber}
                  </option>
                ))}
            </select>
          </label>
        )}
        {mode === "procedures" && (
          <a
            className="secondary"
            href={"/api/v1/reports/procedures.csv?" + params}
            download
          >
            <Download size={16} />
            Export CSV
          </a>
        )}
      </div>
      <ErrorBox error={error} />
      {isLoading ? (
        <div className="skeleton">Calculating report…</div>
      ) : (
        data && (
          <><div>{mode === "procedures" && <ProcedureCharts report={data as import("./api/contracts").ProcedureReport} onDoctor={setDoctor}/>} {mode === "capacity" && <CapacityChart rooms={data as import("./api/contracts").RoomCapacity[]} onRoom={id => {setRoom(id);setMode("census");}}/>}</div><section className="panel table-panel">
            {mode === "procedures" ? (
              <>
                <div className="report-total">
                  <span>{data.rows?.length || 0} performed procedures</span>
                  <strong>{money(data.totalCost)}</strong>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Patient</th>
                      <th scope="col">Procedure</th>
                      <th scope="col">Doctor</th>
                      <th scope="col">Performed at</th>
                      <th scope="col">Recorded cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.rows?.map((v: Row) => (
                      <tr key={v.record.id}>
                        <td>{fullName(v.patient)}</td>
                        <td>{v.procedure.procedureName}</td>
                        <td>{fullName(v.doctor)}</td>
                        <td>{date(v.record.performedAt)}</td>
                        <td>{money(v.record.priceAtExecution)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {!data.rows?.length && (
                  <Empty text="No procedures in this period." />
                )}
                <div className="report-groups">
                  {Object.entries(data.byDoctor || {}).map(([id, total]) => (
                    <span key={id}>
                      {Array.isArray(doctors) &&
                        fullName(doctors.find((d: Row) => String(d.id) === id))}
                      <strong>{money(Number(total))}</strong>
                    </span>
                  ))}
                </div>
              </>
            ) : mode === "census" ? (
              <table>
                <thead>
                  <tr>
                    <th scope="col">Patient</th>
                    <th scope="col">Doctor</th>
                    <th scope="col">Room</th>
                    <th scope="col">Admitted</th>
                  </tr>
                </thead>
                <tbody>
                  {Array.isArray(data) &&
                    data.map((v: Row) => (
                      <tr key={v.admission.id}>
                        <td>
                          <Link to={"/app/patients/" + v.patient.id}>
                            {fullName(v.patient)}
                          </Link>
                        </td>
                        <td>{fullName(v.doctor)}</td>
                        <td>
                          {
                            v.rooms.find((r: Row) => !r.assignment.releasedAt)
                              ?.room.roomNumber
                          }
                        </td>
                        <td>{date(v.admission.admissionDateTime)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th scope="col">Room</th>
                    <th scope="col">Total beds</th>
                    <th scope="col">Occupied</th>
                    <th scope="col">Available</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {Array.isArray(data) &&
                    data.map((r: Row) => (
                      <tr key={r.id}>
                        <td>{r.roomNumber}</td>
                        <td>{r.bedCount}</td>
                        <td>{r.occupiedBeds}</td>
                        <td>{r.availableBeds}</td>
                        <td>{r.active ? "Active" : "Inactive"}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            )}
          </section></>
        )
      )}
    </>
  );
}
function Audit() {
  const { data, error, isLoading } = useData("/audit");
  return (
    <>
      <Title
        eyebrow="ACCOUNTABILITY"
        title="A trace of every change."
        description="The latest 100 audit events. Patient notes and passwords are excluded."
      />
      <ErrorBox error={error} />
      <section className="panel table-panel">
        <table>
          <thead>
            <tr>
              <th scope="col">Timestamp</th>
              <th scope="col">Event</th>
              <th scope="col">Entity</th>
              <th scope="col">User ID</th>
              <th scope="col">Source</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) &&
              data.map((a: Row) => (
                <tr key={a.id}>
                  <td>{date(a.timestamp)}</td>
                  <td className="mono">{a.eventType}</td>
                  <td>
                    {a.entityType} #{a.entityId}
                  </td>
                  <td>{a.userId}</td>
                  <td>{a.source}</td>
                </tr>
              ))}
          </tbody>
        </table>
        {isLoading && <div className="skeleton">Loading audit trail…</div>}
      </section>
    </>
  );
}

function Assistant({ onClose }: { onClose: () => void }) {
  const user = useUser();
  const router = useRouter(),
    pathname = usePathname(),
    client = useQueryClient();
  const [message, setMessage] = useState(""),
    [session, setSession] = useState<string | null>(null),
    [results, setResults] = useState<Row[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<Error | null>(null);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    return () => ref.current?.close();
  }, []);
  async function send(text: string) {
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      const raw = await api("/assistant/messages", "POST", {
        message: text,
        sessionId: session,
        route: pathname,
        selectedPatientId: /\/patients\/\d+$/.test(pathname)
          ? Number(pathname.split("/").pop())
          : null,
      });
      const result = aiResponse.parse(raw);
      setSession(result.sessionId);
      setResults((r) => [...r, { query: text, ...result }]);
    } catch (e) {
      setError(e as Error);
    } finally {
      setBusy(false);
    }
  }
  async function action(id: number, op: string, index: number) {
    setBusy(true);
    setError(null);
    try {
      await api(`/ai-actions/${id}/${op}`, "POST");
      setResults((r) =>
        r.map((v, i) =>
          i === index
            ? { ...v, done: op === "confirm" ? "EXECUTED" : "CANCELLED" }
            : v,
        ),
      );
      await client.invalidateQueries();
    } catch (e) {
      setError(e as Error);
      await client.invalidateQueries();
    } finally {
      setBusy(false);
    }
  }
  return (
    <dialog className="assistant-dialog" ref={ref} onCancel={onClose}>
      <div className="assistant-head">
        <div>
          <Sparkles size={22} />
          <span>
            Operations assistant<small>MEDCORE / COMMAND</small>
          </span>
        </div>
        <button className="icon" aria-label="Close assistant" onClick={onClose}>
          <X />
        </button>
      </div>
      <div className="assistant-content">
        <span className="eyebrow">A SHORTER PATH TO THE ANSWER</span>
        <h2>What needs your attention?</h2>
        <p>
          Find records, explore capacity, or prepare a task for your
          confirmation.
        </p>
        <CommandResults query={message} onClose={onClose} user={user}/>
        <div className="suggestions">
          {[
            "Show department status",
            "Rooms with two free beds",
            "Procedures today",
            "Find Petrov",
          ].map((q) => (
            <button key={q} onClick={() => send(q)} disabled={busy}>
              {q}
              <ArrowUpRight size={14} />
            </button>
          ))}
        </div>
        {results.map((r, i) => (
          <div className="ai-result" key={i}>
            <div className="ai-query">› {r.query}</div>
            <p>{r.message}</p>
            <small className="ai-mode">
              {r.model === "local-command-model"
                ? "Local command mode"
                : "Configured model"}{" "}
              · Backend-authorized results
            </small>
            {r.responseType === "PATIENT_LIST" &&
              r.data.patients.map((p: Row) => (
                <button
                  className="result-row"
                  key={p.id}
                  onClick={() => {
                    router.push("/app/patients/" + p.id);
                    onClose();
                  }}
                >
                  <span>
                    {fullName(p)}
                    <small>{p.patientIdentifier}</small>
                  </span>
                  <ArrowUpRight size={16} />
                </button>
              ))}
            {r.responseType === "ROOM_LIST" &&
              r.data.rooms.map((room: Row) => (
                <div className="result-row" key={room.id}>
                  <span>Room {room.roomNumber}</span>
                  <strong>{room.availableBeds} free</strong>
                </div>
              ))}
            {r.responseType === "PATIENT_SUMMARY" && (
              <>
                <h3>{fullName(r.data.patient)}</h3>
                <p>{r.data.admissions.length} recorded hospitalizations.</p>
                {r.data.admissions.map((v: Row) => (
                  <div className="result-row" key={v.admission.id}>
                    <span>
                      {v.admission.admissionNumber}
                      <small>Dr. {fullName(v.doctor)}</small>
                    </span>
                    <Status value={v.admission.status} />
                  </div>
                ))}
                <button
                  className="secondary"
                  onClick={() => {
                    router.push("/app/patients/" + r.data.patient.id);
                    onClose();
                  }}
                >
                  Open dossier
                </button>
              </>
            )}
            {r.responseType === "REPORT_RESULT" && <AiReport data={r.data} />}
            {r.responseType === "NAVIGATION_COMMAND" && (
              <button
                className="primary"
                onClick={() => {
                  if (
                    /^\/app\/(dashboard|patients(?:\/\d+)?|rooms|admissions|doctors|procedures|reports|users)$/.test(
                      r.data.route,
                    )
                  ) {
                    router.push(r.data.route);
                    onClose();
                  }
                }}
              >
                Open view
                <ArrowRight size={16} />
              </button>
            )}
            {r.responseType === "CONFIRMATION_CARD" && (
              <div className="confirmation">
                <span className="eyebrow">
                  {r.data.action.actionType} PROPOSAL
                </span>
                <h3>{fullName(r.data.patient)}</h3>
                <ProposalPreview current={r.data.current} destination={r.data.destination} actionType={r.data.action.actionType} expiresAt={r.data.action.expiresAt}/>
                {r.data.current && (
                  <p>
                    Current room:{" "}
                    {
                      r.data.current.rooms.find(
                        (x: Row) => !x.assignment.releasedAt,
                      )?.room.roomNumber
                    }
                  </p>
                )}
                {r.data.destination && (
                  <p>Destination: Room {r.data.destination.roomNumber}</p>
                )}
                {r.data.doctor && <p>Doctor: {fullName(r.data.doctor)}</p>}
                <p>Expires {date(r.data.action.expiresAt)}</p>
                {r.done ? (
                  <Status value={r.done} />
                ) : (
                  <div className="actions">
                    <button
                      className="secondary"
                      disabled={busy}
                      onClick={() => action(r.data.action.id, "cancel", i)}
                    >
                      Cancel proposal
                    </button>
                    <button
                      className="primary"
                      disabled={busy || Date.parse(r.data.action.expiresAt) <= Date.now()}
                      onClick={() => action(r.data.action.id, "confirm", i)}
                    >
                      Confirm {r.data.action.actionType.toLowerCase()}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
        {busy && (
          <div className="analyzing">
            <Activity size={18} />
            Checking operational state…
          </div>
        )}
        <ErrorBox error={error} />
      </div>
      <form
        className="assistant-input"
        onSubmit={(e) => {
          e.preventDefault();
          send(message);
        }}
      >
        <label className="sr-only" htmlFor="assistant-message">
          Assistant message
        </label>
        <input
          id="assistant-message"
          autoFocus
          placeholder="Ask about your department…"
          value={message}
          maxLength={2000}
          onChange={(e) => setMessage(e.target.value)}
        />
        <button
          className="primary"
          disabled={busy || !message.trim()}
          aria-label="Send message"
        >
          <ArrowRight size={18} />
        </button>
      </form>
      <div className="assistant-footer">
        <span>Operational support · Human-confirmed changes</span>
        <button
          className="text-button"
          onClick={async () => {
            if (session)
              await api(`/assistant/sessions/${session}/clear`, "POST");
            setSession(null);
            setResults([]);
          }}
        >
          Clear
        </button>
      </div>
    </dialog>
  );
}
function AiReport({ data: d }: { data: Row }) {
  if (d.activeAdmissions !== undefined)
    return (
      <div className="ai-stats">
        <span>
          <strong>{d.activeAdmissions}</strong>Active admissions
        </span>
        <span>
          <strong>{d.availableBeds}</strong>Available beds
        </span>
        <span>
          <strong>{d.proceduresToday}</strong>Procedures today
        </span>
      </div>
    );
  if (d.rows)
    return (
      <>
        <p>
          {d.rows.length} procedures · {money(d.totalCost)}
        </p>
        {d.rows.map((r: Row) => (
          <div className="result-row" key={r.record.id}>
            <span>
              {r.procedure.procedureName}
              <small>{fullName(r.patient)}</small>
            </span>
            <strong>{money(r.record.priceAtExecution)}</strong>
          </div>
        ))}
      </>
    );
  if (d.admissions)
    return (
      <>
        {d.admissions.map((v: Row) => (
          <div className="result-row" key={v.admission.id}>
            <span>{fullName(v.patient)}</span>
            <Status value={v.admission.status} />
          </div>
        ))}
      </>
    );
  if (d.admission)
    return (
      <p>
        {d.admission.admissionNumber} · {fullName(d.patient)}
      </p>
    );
  return <p>No matching records.</p>;
}
export default function MedcoreApp() {
  return (
    <QueryClientProvider client={qc}>
      <CinematicProvider>
        <App />
      </CinematicProvider>
    </QueryClientProvider>
  );
}
