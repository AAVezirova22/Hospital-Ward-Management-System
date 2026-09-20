"use client";
import React, { useEffect, useState, useCallback } from "react";
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { Activity } from "lucide-react";
import { api, bindAccount, logout, type User } from "./api";
import { Auth } from "./components/workspace";
import { WakeScreen } from "./features/demo/DemoAccess";
import { Login } from "./features/auth/LoginScreen";
import { PatientPortal } from "./features/patients/PatientPortal";
import { CinematicProvider } from "./cinematic";
import { Shell } from "./components/workspace-shell";

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
