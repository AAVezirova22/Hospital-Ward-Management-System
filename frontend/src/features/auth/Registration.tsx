"use client";
import { useEffect, useState } from "react";
import { api } from "../../api";

export function Registration({ onBack }: { onBack: () => void }) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [requestedRole, setRole] = useState("PATIENT"),
    [hospitals, setHospitals] = useState<{ id: number; name: string }[]>([]);
  useEffect(() => {
    api<{ id: number; name: string }[]>("/registration/hospitals")
      .then(setHospitals)
      .catch((e) => setError((e as Error).message));
  }, []);
  return (
    <form
      className="login-form registration-form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (busy) return;
        const form = new FormData(e.currentTarget);
        setBusy(true);
        setError("");
        try {
          const response = await api<{ message: string }>(
            "/registration/signup",
            "POST",
            {
              username: form.get("username"),
              email: form.get("email"),
              password: form.get("password"),
              firstName: form.get("firstName"),
              lastName: form.get("lastName"),
              dateOfBirth: form.get("dateOfBirth"),
              requestedRole,
              hospitalId: Number(form.get("hospitalId")),
            },
          );
          setNotice(response.message);
        } catch (e) {
          setError((e as Error).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <span className="eyebrow">YOUR MEDCORE ACCOUNT</span>
      <h2>Your care, connected.</h2>
      <p className="form-intro">
        Create your patient account. Confirm your email to sign in.
      </p>
      {notice ? (
        <div role="status">
          <h3>Check your inbox</h3>
          <p>{notice}</p>
          <button type="button" className="primary" onClick={onBack}>
            Back to sign in
          </button>
        </div>
      ) : (
        <>
          <div className="registration-names">
            <label>
              First name
              <input
                name="firstName"
                autoComplete="given-name"
                maxLength={100}
                required
              />
            </label>
            <label>
              Last name
              <input
                name="lastName"
                autoComplete="family-name"
                maxLength={100}
                required
              />
            </label>
          </div>
          <label>
            Date of birth
            <input
              type="date"
              name="dateOfBirth"
              max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)}
              autoComplete="bday"
              required
            />
          </label>
          <label>
            Hospital
            <select name="hospitalId" required defaultValue="">
              <option value="" disabled>
                Select your hospital
              </option>
              {hospitals.map((hospital) => (
                <option key={hospital.id} value={hospital.id}>
                  {hospital.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Email
            <input
              type="email"
              name="email"
              autoComplete="email"
              maxLength={254}
              required
            />
          </label>
          <label>
            Username
            <input
              name="username"
              autoComplete="username"
              pattern="[a-zA-Z0-9._-]{3,64}"
              minLength={3}
              maxLength={64}
              title="3–64 letters, numbers, dots, underscores or hyphens"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              name="password"
              autoComplete="new-password"
              minLength={12}
              maxLength={72}
              required
            />
          </label>
          <small>At least 12 characters, at most 72 UTF-8 bytes.</small>
          <label>
            Account request
            <select
              value={requestedRole}
              onChange={(e) => setRole(e.target.value)}
            >
              <option value="PATIENT">Patient account</option>
              <option value="DOCTOR">
                Patient account + doctor access request
              </option>
            </select>
          </label>
          {requestedRole === "DOCTOR" && (
            <p className="registration-note">
              Confirm your email first. An administrator reviews doctor requests
              and links approved accounts to a doctor profile.
            </p>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="primary" disabled={busy || hospitals.length === 0}>
            {busy ? "Sending confirmation…" : "Create account"}
          </button>
          <button
            className="text-button"
            type="button"
            disabled={busy}
            onClick={onBack}
          >
            Back to sign in
          </button>
        </>
      )}
    </form>
  );
}
export function EmailVerification({
  token,
  onBack,
}: {
  token: string;
  onBack: () => void;
}) {
  const [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState("");
  return (
    <div className="login-form">
      <span className="eyebrow">EMAIL CONFIRMATION</span>
      <h2>One step to your workspace.</h2>
      <p>
        Confirm that this is your email address to activate your patient
        account.
      </p>
      {message ? (
        <p role="status">{message}</p>
      ) : (
        <button
          className="primary"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError("");
            try {
              const result = await api<{ message: string }>(
                "/registration/verify",
                "POST",
                { token },
              );
              setMessage(result.message);
              window.history.replaceState(null, "", window.location.pathname);
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? "Confirming…" : "Confirm my email"}
        </button>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <button className="text-button" onClick={onBack}>
        Back to sign in
      </button>
    </div>
  );
}
export function ResendConfirmation() {
  const [email, setEmail] = useState(""),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState("");
  return (
    <details className="resend-confirmation">
      <summary>Need another confirmation email?</summary>
      <label>
        Email address
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <button
        type="button"
        className="secondary"
        disabled={busy || !email.includes("@")}
        onClick={async () => {
          setBusy(true);
          try {
            const r = await api<{ message: string }>(
              "/registration/resend",
              "POST",
              { email },
            );
            setMessage(r.message);
          } catch (e) {
            setMessage((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy ? "Sending…" : "Resend confirmation"}
      </button>
      {message && <p role="status">{message}</p>}
    </details>
  );
}