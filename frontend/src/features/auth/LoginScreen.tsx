"use client";
import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowRight, ShieldCheck } from "../../icons";
import { login, type User } from "../../api";
import { ErrorBox } from "../../components/workspace";
import { DemoAccess } from "../demo/DemoAccess";
import {
  Registration,
  EmailVerification,
  ResendConfirmation,
  RecoverRegistration,
} from "./Registration";
import { LoginScene } from "../../login-scene";

export function Login({ onLogin }: { onLogin: (u: User) => void }) {
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
          <ShieldCheck size={24} strokeWidth={1.5} />
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
        <RecoverRegistration />
        <DemoAccess onLogin={onLogin} />
      </motion.form>
    </LoginScene>
  );
}
