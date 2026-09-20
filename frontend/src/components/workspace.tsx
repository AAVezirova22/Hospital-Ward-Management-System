"use client";
import React, { createContext, useContext, useEffect, useRef } from "react";
import NextLink from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertCircle, ClipboardList, X } from "lucide-react";
import { api, activeDepartment, type Row, type User } from "../api";
export const Auth = createContext<User>(null!);
export const useUser = () => useContext(Auth);
export const useData = (key: string, path = key) =>
  useQuery<Row>({
    queryKey: [key, activeDepartment()],
    queryFn: () => api(path),
  });
export function Link({
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

export function ErrorBox({ error }: { error: any }) {
  return error ? (
    <div className="error" role="alert">
      <AlertCircle size={17} />
      {error.message || String(error)}
    </div>
  ) : null;
}
export function Empty({ text = "No records found." }: { text?: string }) {
  return (
    <div className="empty">
      <ClipboardList size={26} />
      <p>{text}</p>
    </div>
  );
}
export function Status({ value }: { value: string }) {
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
export function Modal({
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
export function Title({
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
