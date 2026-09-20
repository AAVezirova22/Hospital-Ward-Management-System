"use client";
import { useEffect, useRef, useState } from "react";
import { api } from "../../api";
import {
  ACCEPTED_FILES,
  directoryPicker,
  indexDirectory,
  requestedFiles,
  selectedFolder,
  type LocalFile,
} from "./local-files";

type Source = {
  id: string;
  name: string;
  characters: number;
  expiresAt: string;
  localId?: string;
};

export function useAssistantSources() {
  const [sources, setSources] = useState<Source[]>([]);
  const [files, setFiles] = useState<LocalFile[]>([]);
  const [notice, setNotice] = useState("");
  const current = useRef<Source[]>([]);
  function update(next: Source[]) {
    current.current = next;
    setSources(next);
  }

  useEffect(
    () => () => {
      for (const source of current.current)
        void api("/assistant/sources/" + source.id, "DELETE").catch(() => {});
    },
    [],
  );

  async function add(file: File, localId?: string): Promise<Source> {
    if (current.current.length >= 20)
      throw new Error("Remove an attachment before adding more (20 maximum).");
    if (!file.size || file.size > 5 * 1024 * 1024)
      throw new Error(file.name + ": choose a nonempty file up to 5 MB.");
    const data = new FormData();
    data.append("file", file);
    const source = {
      ...(await api<Source>("/assistant/sources", "POST", data)),
      localId,
    };
    update([...current.current, source]);
    return source;
  }
  async function remove(id: string) {
    await api("/assistant/sources/" + id, "DELETE").catch((e) => {
      if (e.status !== 404) throw e;
    });
    update(current.current.filter((s) => s.id !== id));
  }
  async function disconnect() {
    for (const source of current.current.filter((s) => s.localId))
      await remove(source.id);
    setFiles([]);
    setNotice("");
  }
  async function clear() {
    for (const source of [...current.current]) await remove(source.id);
    setFiles([]);
    setNotice("");
  }
  function useFolder(result: Awaited<ReturnType<typeof indexDirectory>>) {
    setFiles(result.files);
    setNotice(
      result.files.length +
        " readable file names connected." +
        (result.limited
          ? " Scan limit reached; choose a smaller folder to include remaining files."
          : "") +
        (result.skipped ? " Unsupported and hidden files were skipped." : ""),
    );
  }
  async function read(ids: string[]) {
    const pending = ids.filter(
      (id) => !current.current.some((s) => s.localId === id),
    );
    if (current.current.length + pending.length > 20)
      throw new Error(
        "This request needs more than 20 attachments. Narrow the request or remove files.",
      );
    if (pending.length) {
      const selected = await requestedFiles(pending, files);
      for (let i = 0; i < selected.length; i++)
        await add(selected[i], pending[i]);
    }
    return current.current.map((s) => s.id);
  }
  return {
    sources,
    files,
    notice,
    add,
    remove,
    disconnect,
    clear,
    useFolder,
    read,
    sourceIds: () => current.current.map((s) => s.id),
    manifest: () =>
      files
        .filter((f) => !current.current.some((s) => s.localId === f.id))
        .map(({ id, name }) => ({ id, name })),
  };
}

export function AssistantSources({
  model,
  busy,
  run,
}: {
  model: ReturnType<typeof useAssistantSources>;
  busy: boolean;
  run: (action: () => Promise<void>) => void;
}) {
  const upload = useRef<HTMLInputElement>(null);
  const folder = useRef<HTMLInputElement>(null);
  return (
    <section className="assistant-sources" aria-label="Assistant files">
      <div className="actions">
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() => upload.current?.click()}
        >
          Upload files
        </button>
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() => {
            const picker = directoryPicker();
            if (!picker) {
              folder.current?.click();
              return;
            }
            // Invoke the native picker directly during the click gesture.
            const selection = picker({ mode: "read" });
            run(async () => {
              const handle = await selection;
              await model.disconnect();
              model.useFolder(await indexDirectory(handle));
            });
          }}
        >
          Connect folder
        </button>
        {!!model.files.length && (
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => run(model.disconnect)}
          >
            Disconnect folder
          </button>
        )}
      </div>
      <input
        ref={upload}
        type="file"
        multiple
        accept={ACCEPTED_FILES}
        hidden
        aria-label="Upload assistant files"
        onChange={(e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          run(async () => {
            for (const file of files) await model.add(file);
          });
        }}
      />
      <input
        ref={folder}
        type="file"
        multiple
        hidden
        aria-label="Choose assistant folder"
        {...({ webkitdirectory: "" } as Record<string, string>)}
        onChange={(e) => {
          const files = e.target.files;
          if (!files) return;
          const result = selectedFolder(files);
          e.target.value = "";
          run(async () => {
            await model.disconnect();
            model.useFolder(result);
          });
        }}
      />
      <p className="ai-mode">
        Choose files or connect a folder on this PC. File names and requested
        contents are shared with the configured AI provider when you send a
        message.
      </p>
      {model.notice && <p role="status">{model.notice}</p>}
      {model.sources.map((source) => (
        <div className="result-row" key={source.id}>
          <span>
            {source.name}
            <small>
              {source.characters.toLocaleString()} characters · available for 30
              minutes
            </small>
          </span>
          <button
            type="button"
            className="text-button"
            disabled={busy}
            aria-label={"Remove " + source.name}
            onClick={() => run(() => model.remove(source.id))}
          >
            Remove
          </button>
        </div>
      ))}
    </section>
  );
}
