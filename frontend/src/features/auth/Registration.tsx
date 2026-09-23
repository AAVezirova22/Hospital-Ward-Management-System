"use client";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api } from "../../api";

const signupSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, "Enter your first name.")
    .max(100, "Use 100 characters or fewer."),
  lastName: z
    .string()
    .trim()
    .min(1, "Enter your last name.")
    .max(100, "Use 100 characters or fewer."),
  dateOfBirth: z.string().min(1, "Enter your date of birth."),
  hospitalId: z.coerce
    .number()
    .int("Select a valid hospital.")
    .positive("Select a hospital."),
  email: z
    .string()
    .trim()
    .email("Enter a valid email address.")
    .max(254, "Email addresses must be 254 characters or fewer."),
  username: z
    .string()
    .regex(
      /^[a-zA-Z0-9._-]{3,64}$/,
      "Use 3-64 letters, numbers, dots, underscores, or hyphens.",
    ),
  password: z
    .string()
    .min(12, "Use at least 12 characters.")
    .max(72, "Use no more than 72 characters."),
  requestedRole: z.enum(["PATIENT", "DOCTOR"], {
    error: "Choose an account request.",
  }),
});

type SignupValues = z.infer<typeof signupSchema>;
type SignupInput = z.input<typeof signupSchema>;

function FieldFeedback({ id, message }: { id: string; message?: string }) {
  return message ? (
    <small id={id} className="invalid">
      {message}
    </small>
  ) : null;
}

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
  } = useForm<SignupInput, unknown, SignupValues>({
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
      noValidate
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
            <div className="registration-field">
              <label htmlFor="registration-first-name">
                First name
                <input
                  id="registration-first-name"
                  autoComplete="given-name"
                  maxLength={100}
                  aria-invalid={Boolean(errors.firstName)}
                  aria-describedby={
                    errors.firstName
                      ? "registration-first-name-error"
                      : undefined
                  }
                  {...register("firstName")}
                />
              </label>
              <FieldFeedback
                id="registration-first-name-error"
                message={errors.firstName?.message}
              />
            </div>
            <div className="registration-field">
              <label htmlFor="registration-last-name">
                Last name
                <input
                  id="registration-last-name"
                  autoComplete="family-name"
                  maxLength={100}
                  aria-invalid={Boolean(errors.lastName)}
                  aria-describedby={
                    errors.lastName ? "registration-last-name-error" : undefined
                  }
                  {...register("lastName")}
                />
              </label>
              <FieldFeedback
                id="registration-last-name-error"
                message={errors.lastName?.message}
              />
            </div>
          </div>
          <div className="registration-field">
            <label htmlFor="registration-date-of-birth">
              Date of birth
              <input
                id="registration-date-of-birth"
                type="date"
                max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)}
                autoComplete="bday"
                aria-invalid={Boolean(errors.dateOfBirth)}
                aria-describedby={
                  errors.dateOfBirth
                    ? "registration-date-of-birth-error"
                    : undefined
                }
                {...register("dateOfBirth")}
              />
            </label>
            <FieldFeedback
              id="registration-date-of-birth-error"
              message={errors.dateOfBirth?.message}
            />
          </div>
          <div className="registration-field">
            <label htmlFor="registration-hospital">
              Hospital
              <select
                id="registration-hospital"
                aria-invalid={Boolean(errors.hospitalId)}
                aria-describedby={
                  errors.hospitalId ? "registration-hospital-error" : undefined
                }
                {...register("hospitalId")}
                defaultValue=""
              >
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
            <FieldFeedback
              id="registration-hospital-error"
              message={errors.hospitalId?.message}
            />
          </div>
          <div className="registration-field">
            <label htmlFor="registration-email">
              Email
              <input
                id="registration-email"
                type="email"
                autoComplete="email"
                maxLength={254}
                aria-invalid={Boolean(errors.email)}
                aria-describedby={
                  errors.email ? "registration-email-error" : undefined
                }
                {...register("email")}
              />
            </label>
            <FieldFeedback
              id="registration-email-error"
              message={errors.email?.message}
            />
          </div>
          <div className="registration-field">
            <label htmlFor="registration-username">
              Username
              <input
                id="registration-username"
                autoComplete="username"
                maxLength={64}
                title="3–64 letters, numbers, dots, underscores or hyphens"
                aria-invalid={Boolean(errors.username)}
                aria-describedby={
                  errors.username ? "registration-username-error" : undefined
                }
                {...register("username")}
              />
            </label>
            <FieldFeedback
              id="registration-username-error"
              message={errors.username?.message}
            />
          </div>
          <div className="registration-field">
            <label htmlFor="registration-password">
              Password
              <input
                id="registration-password"
                type="password"
                autoComplete="new-password"
                aria-invalid={Boolean(errors.password)}
                aria-describedby={
                  errors.password
                    ? "registration-password-error registration-password-hint"
                    : "registration-password-hint"
                }
                {...register("password")}
              />
            </label>
            <FieldFeedback
              id="registration-password-error"
              message={errors.password?.message}
            />
            <small id="registration-password-hint">
              At least 12 characters, at most 72 UTF-8 bytes.
            </small>
          </div>
          <div className="registration-field">
            <label htmlFor="registration-requested-role">
              Account request
              <select
                id="registration-requested-role"
                aria-invalid={Boolean(errors.requestedRole)}
                aria-describedby={
                  errors.requestedRole
                    ? "registration-requested-role-error"
                    : undefined
                }
                {...register("requestedRole")}
              >
                <option value="PATIENT">Patient account</option>
                <option value="DOCTOR">
                  Patient account + doctor access request
                </option>
              </select>
            </label>
            <FieldFeedback
              id="registration-requested-role-error"
              message={errors.requestedRole?.message}
            />
          </div>
          {requestedRole === "DOCTOR" && (
            <p className="registration-note">
              Confirm your email first. An administrator reviews doctor requests
              and links approved accounts to a doctor profile.
            </p>
          )}
          {(error || Object.keys(errors).length > 0) && (
            <p className="error" role="alert">
              {error || "Please correct the highlighted fields and try again."}
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
