"use client";

import { useState } from "react";
import { ArrowRight } from "@phosphor-icons/react";
import { EcgLine } from "./EcgLine";

const DEPARTMENTS = [
  "Cardiology",
  "Neurology",
  "Oncology",
  "Orthopedics",
  "Another specialty",
];

export function Booking() {
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  return (
    <div
      id="booking"
      className="film-booking"
      role="dialog"
      aria-modal="true"
      aria-labelledby="booking-title"
    >
      {sent ? (
        <div className="film-booking-done">
          <h2 id="booking-title">We have the request</h2>
          <p>
            A coordinator will call you to confirm the time. Nothing is booked
            until that call.
          </p>
          <a className="film-btn" href="#care">
            Close
          </a>
        </div>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const data = new FormData(event.currentTarget);
            const name = String(data.get("name") || "").trim();
            const phone = String(data.get("phone") || "").trim();
            if (name.length < 2) {
              setError("Enter the name we should ask for.");
              return;
            }
            if (phone.length < 6) {
              setError("Enter a phone number we can reach.");
              return;
            }
            setError("");
            setSent(true);
          }}
        >
          <h2 id="booking-title">Book an Appointment</h2>
          <EcgLine className="film-ecg" draw />
          <label className="film-field" htmlFor="booking-name">
            Full name
            <input id="booking-name" name="name" autoComplete="name" required />
          </label>
          <label className="film-field" htmlFor="booking-phone">
            Phone
            <input
              id="booking-phone"
              name="phone"
              type="tel"
              autoComplete="tel"
              required
            />
          </label>
          <label className="film-field">
            Department
            <select name="department" defaultValue="Cardiology">
              {DEPARTMENTS.map((dept) => (
                <option key={dept}>{dept}</option>
              ))}
            </select>
          </label>
          <label className="film-field">
            Preferred date
            <input name="date" type="date" />
          </label>
          {error ? <p className="film-error">{error}</p> : null}
          <div className="film-booking-actions">
            <a className="film-ghost" href="#care">
              Cancel
            </a>
            <button className="film-btn" type="submit">
              Request a time
              <span className="film-btn-ico" aria-hidden="true">
                <ArrowRight size={14} weight="light" />
              </span>
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
