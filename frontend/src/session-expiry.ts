export function createSessionExpiry(
  clearClientSession: () => void,
  revokeServerSession: () => Promise<unknown>,
) {
  let expiring = false;
  let revocation: Promise<void> | null = null;

  return {
    expire() {
      if (expiring) return revocation ?? Promise.resolve();

      expiring = true;
      clearClientSession();
      revocation = Promise.resolve()
        .then(revokeServerSession)
        .then(
          () => undefined,
          () => undefined,
        );
      return revocation;
    },
    reset() {
      expiring = false;
      revocation = null;
    },
    get isExpiring() {
      return expiring;
    },
  };
}
