"use client";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api } from "../../api";

const signupSchema = z.object({
  firstName: z.string().trim().min(1).max(100),
  lastName: z.string().trim().min(1).max(100),
  dateOfBirth: z.string().min(1),
  hospitalId: z.coerce.number().int().positive(),
  email: z.string().trim().email().max(254),
  username: z.string().regex(/^[a-zA-Z0-9._-]{3,64}$/),
  password: z.string().min(12).max(72),
  requestedRole: z.enum(["PATIENT", "DOCTOR"]),
});

type SignupBody = z.output<typeof signupSchema>;
type SignupValues = z.infer<typeof signupSchema>;
type SignupInput = z.input<typeof signupSchema>;

export function Registration({ onBack }: { onBack: () => void }) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [hospitals, setHospitals] = useState<{ id: number; name: string }[]>([]);
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<SignupValues, unknown, SignupBody, SignupInput>({
    resolver: zodResolver(signupSchema),
    defaultValues: { requestedRole: "PATIENT" },
  });
  const requestedRole = watch("requestedRole");
  useEffect(() => {
    api<{ id: number; name: string }[]>("/registration/hospitals")
      .then(setHospitals)
      .catch((e) => setError((e as Error).message));
  }, []);
  return (
    <form
      className="login-form registration-form"
      onSubmit={handleSubmit(async (values) => {
        if (busy) return;
        setBusy(true);
        setError("");
        try {
          const response = await api<{ message: string }>(
            "/registration/signup",
            "POST",
            values,
          );
          setNotice(response.message);
        } catch (e) {
          setError((e as Error).message);
        } finally {
          setBusy(false);
        }
      })}
    >
      <span className="eyebrow">Your Medcore account</span>
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
                autoComplete="given-name"
                maxLength={100}
                {...register("firstName")}
              />
            </label>
            <label>
              Last name
              <input
                autoComplete="family-name"
                maxLength={100}
                {...register("lastName")}
              />
            </label>
          </div>
          <label>
            Date of birth
            <input
              type="date"
              max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)}
              autoComplete="bday"
              {...register("dateOfBirth")}
            />
          </label>
          <label>
            Hospital
            <select {...register("hospitalId")} defaultValue="">
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
              autoComplete="email"
              maxLength={254}
              {...register("email")}
            />
          </label>
          <label>
            Username
            <input
              autoComplete="username"
              maxLength={64}
              title="3–64 letters, numbers, dots, underscores or hyphens"
              {...register("username")}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="new-password"
              {...register("password")}
            />
          </label>
          <small>At least 12 characters, at most 72 UTF-8 bytes.</small>
          <label>
            Account request
            <select {...register("requestedRole")}>
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
          {(error ||
            errors.firstName ||
            errors.username ||
            errors.password ||
            errors.email) && (
            <p className="error" role="alert">
              {error || "Check the highlighted fields and try again."}
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
      <span className="eyebrow">Email confirmation</span>
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
