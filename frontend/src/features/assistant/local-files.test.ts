import { describe, expect, it, vi } from "vitest";
import { indexDirectory, requestedFiles, type DirectoryHandle } from "./local-files";

function directory(name: string, entries: unknown[]): DirectoryHandle {
  return { kind: "directory", name, async *values() { yield* entries as Parameters<typeof directory>[1] as any; } };
}
describe("connected folder access", () => {
  it("indexes supported names without reading bytes and reports scan limits", async () => {
    const getFile = vi.fn();
    const root = directory("Documents", [
      { kind: "file", name: "patients.csv", getFile },
      directory("nested", [{ kind: "file", name: "schedule.xlsx", getFile }]),
      directory(".private", [{ kind: "file", name: "secret.txt", getFile }]),
      { kind: "file", name: "program.exe", getFile },
    ]);
    const result = await indexDirectory(root);
    expect(result.files.map(f => f.name)).toEqual(["Documents/patients.csv", "Documents/nested/schedule.xlsx"]);
    expect(getFile).not.toHaveBeenCalled();
    expect(result.skipped).toBe(2);
    const huge = await indexDirectory(directory("large", Array.from({ length: 301 }, (_, n) => ({ kind: "file", name: n + ".txt", getFile }))));
    expect(huge.files).toHaveLength(300);
    expect(huge.limited).toBe(true);
  });
  it("reads only requested handles and rejects unknown IDs before any read", async () => {
    const getFile = vi.fn(async () => new File(["hello"], "notes.txt"));
    const files = [{ id: "known", name: "notes.txt", getFile }];
    await expect(requestedFiles(["known", "../outside"], files)).rejects.toThrow("no longer connected");
    expect(getFile).not.toHaveBeenCalled();
    const result = await requestedFiles(["known"], files);
    expect(await result[0].text()).toBe("hello");
    expect(getFile).toHaveBeenCalledTimes(1);
    await expect(requestedFiles(["known", "known"], files)).rejects.toThrow("invalid file list");
  });
  it("surfaces revoked permission and rejects oversized files", async () => {
    await expect(requestedFiles(["x"], [{ id: "x", name: "private.txt", getFile: async () => { throw new DOMException("Permission revoked", "NotAllowedError"); } }])).rejects.toThrow("Permission revoked");
    await expect(requestedFiles(["x"], [{ id: "x", name: "large.txt", getFile: async () => new File([new Uint8Array(5 * 1024 * 1024 + 1)], "large.txt") }])).rejects.toThrow("5 MB");
  });
});

