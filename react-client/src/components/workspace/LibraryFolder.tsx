import { memo } from "react";
import type { LibraryFolder as LibraryFolderModel, LibraryItem } from "../../types/physlive";
import Icon from "../common/LearningIcon";
import LibraryItemRow from "./LibraryItem";

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

  return (
    <details className={`learn-library-folder${legacy ? " legacy" : ""}`} open>
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
        {items.length === 0 && <p>Chưa có mô phỏng</p>}
      </div>
    </details>
  );
});

export default LibraryFolder;
