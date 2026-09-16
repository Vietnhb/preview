import { memo } from "react";
import type { LibraryFolder, LibraryItem as LibraryItemModel } from "../../types/physlive";
import LibraryItemActions from "./LibraryItemActions";

type Props = Readonly<{
  item: LibraryItemModel;
  folders: LibraryFolder[];
  currentSimulationId: string;
  opening: boolean;
  onOpen: (item: LibraryItemModel) => Promise<void>;
}>;

/** A memoized library row. Changes to another row do not repaint this item. */
const LibraryItem = memo(function LibraryItem({
  item,
  folders,
  currentSimulationId,
  opening,
  onOpen,
}: Props) {
  return (
    <div className="learn-library-item">
      <button
        type="button"
        className={item.simulationId === currentSimulationId ? "active" : ""}
        disabled={opening}
        onClick={() => void onOpen(item)}
      >
        <span>{opening ? "Đang mở…" : item.title}</span>
        <small>{item.topic ?? "Physics"} · {item.validationStatus}</small>
      </button>
      <LibraryItemActions item={item} folders={folders} />
    </div>
  );
});

export default LibraryItem;
