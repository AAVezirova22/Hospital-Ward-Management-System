export const ACCEPTED_FILES =
  ".txt,.md,.csv,.tsv,.json,.pdf,.docx,.xlsx,.pptx,.odt,.ods,.rtf";
const supported = /\.(txt|md|csv|tsv|json|pdf|docx|xlsx|pptx|odt|ods|rtf)$/i;
export type LocalFile = {
  id: string;
  name: string;
  getFile: () => Promise<File>;
};
export type DirectoryHandle = {
  kind: "directory";
  name: string;
  values(): AsyncIterable<
    DirectoryHandle | { kind: "file"; name: string; getFile(): Promise<File> }
  >;
};
export type DirectoryPicker = (options: {
  mode: "read";
}) => Promise<DirectoryHandle>;

export function directoryPicker(): DirectoryPicker | undefined {
  return (
    window as unknown as { showDirectoryPicker?: DirectoryPicker }
  ).showDirectoryPicker?.bind(window);
}

/** Enumerate names only. Contents stay on the PC until the agent requests a manifest ID. */
export async function indexDirectory(root: DirectoryHandle) {
  const files: LocalFile[] = [];
  let visited = 0,
    skipped = 0,
    limited = false;
  async function walk(
    directory: DirectoryHandle,
    prefix: string,
    depth: number,
  ) {
    for await (const entry of directory.values()) {
      if (++visited > 3000 || files.length >= 300) {
        limited = true;
        return;
      }
      if (
        entry.name.startsWith(".") ||
        ["node_modules", "target", "dist"].includes(entry.name)
      ) {
        skipped++;
        continue;
      }
      const name = prefix + entry.name;
      if (entry.kind === "directory") {
        if (depth >= 8) {
          limited = true;
          continue;
        }
        await walk(entry, name + "/", depth + 1);
        if (limited && (visited > 3000 || files.length >= 300)) return;
      } else if (supported.test(name) && name.length <= 300) {
        files.push({
          id: crypto.randomUUID(),
          name,
          getFile: () => entry.getFile(),
        });
      } else skipped++;
    }
  }
  await walk(root, root.name + "/", 0);
  return { files, skipped, limited };
}

export function selectedFolder(files: FileList): {
  files: LocalFile[];
  skipped: number;
  limited: boolean;
} {
  const all = Array.from(files);
  const allowed = all.filter(
    (f) =>
      supported.test(f.name) && (f.webkitRelativePath || f.name).length <= 300,
  );
  return {
    files: allowed.slice(0, 300).map((file) => ({
      id: crypto.randomUUID(),
      name: file.webkitRelativePath || file.name,
      getFile: async () => file,
    })),
    skipped: all.length - allowed.length,
    limited: allowed.length > 300,
  };
}

export async function requestedFiles(
  ids: string[],
  files: LocalFile[],
): Promise<File[]> {
  if (!ids.length || ids.length > 10 || new Set(ids).size !== ids.length)
    throw new Error("The assistant requested an invalid file list.");
  // Resolve the whole set before reading anything, and never treat IDs as paths.
  const selected = ids.map((id) => {
    const file = files.find((f) => f.id === id);
    if (!file)
      throw new Error(
        "The requested file is no longer connected. Connect the folder again.",
      );
    return file;
  });
  const result: File[] = [];
  for (const source of selected) {
    const file = await source.getFile();
    if (!file.size || file.size > 5 * 1024 * 1024)
      throw new Error(source.name + ": choose a nonempty file up to 5 MB.");
    result.push(file);
  }
  return result;
}
