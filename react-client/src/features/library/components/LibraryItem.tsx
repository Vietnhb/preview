import { memo } from "react";
import type { LibraryFolder, LibraryItem as LibraryItemModel } from "../../../shared/types/physlive";
import LibraryItemActions, { shareStatus } from "./LibraryItemActions";

/** Drag payload type: the id of the library item being moved to another folder. */
export const LIBRARY_ITEM_DRAG_TYPE = "application/x-physlive-library-item";

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
    <div className="learn-library-item" draggable title="Kéo vào một thư mục để chuyển"
      onDragStart={event => {
        event.dataTransfer.setData(LIBRARY_ITEM_DRAG_TYPE, item.id);
        event.dataTransfer.effectAllowed = "move";
      }}>
      <button
        type="button"
        className={item.simulationId === currentSimulationId ? "active" : ""}
        disabled={opening}
        onClick={() => void onOpen(item)}
      >
        <span>{opening ? "Đang mở…" : item.title}</span>
        <small>{item.topic ?? "Vật lý"}{item.visibility !== "PERSONAL" && <> · <span className="library-share-tag" data-state={item.moderationStatus}>{item.visibility === "PUBLIC" ? "Cộng đồng" : "Trường"}: {shareStatus(item).toLocaleLowerCase("vi")}</span></>}</small>
      </button>
      <LibraryItemActions item={item} folders={folders} />
    </div>
  );
});

export default LibraryItem;