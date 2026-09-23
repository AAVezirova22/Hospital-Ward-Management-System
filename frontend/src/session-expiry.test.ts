import { describe, expect, it, vi } from "vitest";
import { createSessionExpiry } from "./session-expiry";

describe("session expiry", () => {
  it("clears client state immediately and revokes the server session once", async () => {
    let finishRevocation!: () => void;
    const clearClientSession = vi.fn();
    const revokeServerSession = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finishRevocation = resolve;
        }),
    );
    const expiry = createSessionExpiry(clearClientSession, revokeServerSession);

    const first = expiry.expire();
    const second = expiry.expire();

    expect(expiry.isExpiring).toBe(true);
    expect(clearClientSession).toHaveBeenCalledTimes(1);
    await Promise.resolve();
    expect(revokeServerSession).toHaveBeenCalledTimes(1);
    finishRevocation();
    await Promise.all([first, second]);

    expiry.reset();
    expect(expiry.isExpiring).toBe(false);
    revokeServerSession.mockResolvedValue(undefined);
    await expiry.expire();
    expect(clearClientSession).toHaveBeenCalledTimes(2);
    expect(revokeServerSession).toHaveBeenCalledTimes(2);
  });

  it("keeps the client signed out when server revocation fails", async () => {
    const clearClientSession = vi.fn();
    const revokeServerSession = vi
      .fn()
      .mockRejectedValue(new Error("network unavailable"));
    const expiry = createSessionExpiry(clearClientSession, revokeServerSession);

    await expect(expiry.expire()).resolves.toBeUndefined();
    await expiry.expire();

    expect(clearClientSession).toHaveBeenCalledTimes(1);
    expect(revokeServerSession).toHaveBeenCalledTimes(1);
    expect(expiry.isExpiring).toBe(true);
  });
});
