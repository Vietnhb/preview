import { memo, useState, type DragEvent } from "react";
import type { LibraryFolder as LibraryFolderModel, LibraryItem } from "../../../shared/types/physlive";
import Icon from "../../../shared/ui/LearningIcon";
import LibraryItemRow, { LIBRARY_ITEM_DRAG_TYPE } from "./LibraryItem";
import { moveLibraryItem } from "../api/libraryApi";
import { teacherLibraryStore } from "../hooks/useTeacherLibrary";
import { getToken } from "../../../shared/lib/token";

type Props = Readonly<{
  folder: LibraryFolderModel | null;
  items: LibraryItem[];
  folders: LibraryFolderModel[];
  currentSimulationId: string;
  openingId: string | null;
  onOpen: (item: LibraryItem) => Promise<void>;
}>;

/** Memoized folder tree node containing its simulation rows. */
const LibraryFolder = memo(function LibraryFolder({
  folder,
  items,
  folders,
  currentSimulationId,
  openingId,
  onOpen,
}: Props) {
  const legacy = folder === null;
  const title = folder?.name ?? "Chưa phân loại";
  // Dropping a library row on a real folder moves it there ("Chưa phân loại" is not a folder).
  const [dropState, setDropState] = useState<"" | "over" | "saving" | "error">("");
  const accepts = (event: DragEvent) => !legacy && event.dataTransfer.types.includes(LIBRARY_ITEM_DRAG_TYPE);
  const drop = async (event: DragEvent) => {
    if (!folder || !accepts(event)) return;
    event.preventDefault();
    event.stopPropagation();
    const itemId = event.dataTransfer.getData(LIBRARY_ITEM_DRAG_TYPE);
    if (!itemId || items.some(item => item.id === itemId)) { setDropState(""); return; }
    const session = getToken();
    setDropState("saving");
    try {
      const updated = await moveLibraryItem(itemId, folder.id);
      if (getToken() === session && teacherLibraryStore.getState().session === session) {
        teacherLibraryStore.setState(state => ({ items: state.items.map(value => value.id === itemId ? updated : value) }));
      }
      setDropState("");
    } catch {
      setDropState("error");
    }
  };

  return (
    <details className={`learn-library-folder${legacy ? " legacy" : ""}`} open data-drop={dropState || undefined}
      onDragOver={event => { if (accepts(event)) { event.preventDefault(); event.dataTransfer.dropEffect = "move"; if (dropState !== "over") setDropState("over"); } }}
      onDragLeave={event => { if (dropState === "over" && !event.currentTarget.contains(event.relatedTarget as Node | null)) setDropState(""); }}
      onDrop={event => void drop(event)}>
      <summary className="learn-library-folder-name">
        <Icon name="folder" />
        <strong title={title}>{title}</strong>
        <small>{items.length}</small>
      </summary>
      <div className="learn-library-items">
        {items.map(item => (
          <LibraryItemRow
            key={item.id}
            item={item}
            folders={folders}
            currentSimulationId={currentSimulationId}
            opening={openingId === item.id}
            onOpen={onOpen}
          />
        ))}
        {items.length === 0 && <p>{legacy ? "Chưa có mô phỏng" : "Chưa có mô phỏng. Kéo mô phỏng vào đây."}</p>}
        {dropState === "saving" && <p role="status">Đang chuyển…</p>}
        {dropState === "error" && <p className="library-drop-error" role="alert">Không chuyển được mô phỏng. Vui lòng thử lại.</p>}
      </div>
    </details>
  );
});

export default LibraryFolder;