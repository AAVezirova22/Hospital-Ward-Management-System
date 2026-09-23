"use client";
import React, { createContext, useContext, useEffect, useRef } from "react";
import NextLink from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertCircle, ClipboardList, X } from "../icons";
import { api, activeDepartment, type Row, type User } from "../api";
export const Auth = createContext<User>(null!);
export const useUser = () => useContext(Auth);
export const useData = (key: string, path = key) =>
  useQuery<Row>({
    queryKey: [key, activeDepartment()],
    queryFn: () => api(path),
    refetchInterval: key === "/rooms" ? 15_000 : undefined,
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

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div className="error" role="alert">
      <AlertCircle size={17} />
      {message}
    </div>
  );
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
  const opener = useRef<HTMLElement | null>(null);
  useEffect(() => {
    opener.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    ref.current?.showModal();
    return () => {
      ref.current?.close();
      opener.current?.focus();
    };
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
  eyebrow?: string;
  title: string;
  description: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}
