import { getSvgAsset } from "../../../simulation-assets/SvgAssetManifest";
import type { AssetSelection } from "../../../types/physlive";
import styles from "./AssetSelectionReview.module.css";

type Props = {
  selection: AssetSelection | null | undefined;
  loading: boolean;
  onDecision: (accepted: boolean) => void;
  onRetry: () => void;
  onRevise: () => void;
  onReset: () => void;
};

export function AssetSelectionReview({
  selection,
  loading,
  onDecision,
  onRetry,
  onRevise,
  onReset,
}: Readonly<Props>) {
  const needsConfirmation = selection?.status === "NEEDS_CONFIRMATION";
  const ready = selection?.status === "READY";
  const previews =
    selection?.choices.map((choice) => ({
      ...choice,
      svg: choice.assetId ? getSvgAsset(choice.assetId) : undefined,
    })) ?? [];
  const canApprove =
    previews.length > 0 && previews.every((choice) => Boolean(choice.svg));
  const needsVisualReview =
    needsConfirmation || selection?.status === "UNSUPPORTED";
  const heading = needsConfirmation
    ? "Xác nhận hình minh họa"
    : ready
      ? "Hình minh họa đã sẵn sàng"
      : selection?.status === "REJECTED"
        ? "Mô phỏng đã dừng theo lựa chọn của bạn"
        : selection?.status === "UNSUPPORTED"
          ? "Không thể tạo mô phỏng"
          : "Chưa hoàn tất hình minh họa";

  return (
    <section
      className={styles.review}
      aria-label="Hình minh họa mô phỏng"
      aria-busy={loading}
    >
      <h3>{heading}</h3>
      <p>
        {needsConfirmation
          ? "Xem phần giải thích cho từng hình đề xuất và xác nhận lựa chọn"
          : ready
            ? "Có thể chạy lại mô phỏng với dữ kiện và hình đã chọn."
            : selection?.status === "REJECTED"
              ? "Bạn đã từ chối hình minh họa. Mô phỏng sẽ không được tạo."
              : selection?.status === "UNSUPPORTED"
                ? "Thư viện hiện chưa có hình phù hợp cho đề bài này. Hãy điều chỉnh đề bài."
                : "Hệ thống chưa hoàn tất kế hoạch hình minh họa nên mô phỏng chưa được chạy. Bạn có thể phân tích lại đề bài."}
      </p>
      {needsVisualReview && (
        <ul className={styles.previews}>
          {previews.map((choice) => (
            <li key={choice.targetId}>
              {choice.svg && (
                <img
                  src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(choice.svg.markup)}`}
                  alt={choice.assetLabel ?? choice.entityLabel}
                />
              )}
              <strong>{choice.entityLabel}</strong>
              <span>{choice.assetLabel ?? "Chưa có hình phù hợp"}</span>
              {choice.visualDifference && <p>{choice.visualDifference}</p>}
              {choice.requiresConfirmation && (
                <small>Hình đề xuất · cần xác nhận</small>
              )}
            </li>
          ))}
        </ul>
      )}
      {needsConfirmation && !canApprove && (
        <p role="alert">Không tải được đầy đủ hình đề xuất để xác nhận.</p>
      )}
      <div className={styles.actions}>
        {needsConfirmation && (
          <>
            <button
              type="button"
              disabled={loading || !canApprove}
              onClick={() => onDecision(true)}
            >
              Đồng ý dùng các hình này
            </button>
            <button
              type="button"
              className={styles.secondary}
              disabled={loading}
              onClick={() => onDecision(false)}
            >
              Từ chối và dừng
            </button>
          </>
        )}
        {ready && (
          <button type="button" disabled={loading} onClick={onRetry}>
            Chạy lại mô phỏng
          </button>
        )}
        <button
          type="button"
          className={styles.secondary}
          disabled={loading}
          onClick={onRevise}
        >
          Sửa đề bài
        </button>
        <button
          type="button"
          className={styles.secondary}
          disabled={loading}
          onClick={onReset}
        >
          Nhập đề khác
        </button>
      </div>
    </section>
  );
}
