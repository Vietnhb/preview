import { useEffect, useRef, useState, type FormEvent } from "react";
import { Avatar, Badge, Button, IconButton, Spinner, TextArea } from "@radix-ui/themes";
import { ChatBubbleIcon, HeartFilledIcon, HeartIcon, PaperPlaneIcon, TrashIcon } from "@radix-ui/react-icons";
import { commentOnLibrary, deleteLibraryComment, libraryDiscussion, reactToLibrary, type LibraryDiscussion } from "../api/libraryApi";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { appendDiscussionPage } from "../model/discussionModel";
import styles from "./ResourceDiscussion.module.css";

const dateFormatter = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" });

export function ResourceDiscussion({ resourceId }: Readonly<{ resourceId: string }>) {
  const user = useSessionStore(state => state.user);
  const [discussion, setDiscussion] = useState<LibraryDiscussion | null>(null);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>("load");
  const controllerRef = useRef<AbortController | null>(null);
  const canInteract = Boolean(user && discussion?.canInteract);

  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    libraryDiscussion(resourceId, 0, controller.signal)
      .then(data => { if (!controller.signal.aborted) setDiscussion(data); })
      .catch(() => { if (!controller.signal.aborted) setError("Không tải được bình luận."); })
      .finally(() => { if (!controller.signal.aborted) setBusy(null); });
    return () => controller.abort();
  }, [resourceId]);

  async function perform(kind: string, action: (signal: AbortSignal) => Promise<void>, message: string) {
    const signal = controllerRef.current?.signal;
    if (!signal || signal.aborted || busy) return;
    setBusy(kind);
    setError("");
    try { await action(signal); }
    catch { if (!signal.aborted) setError(message); }
    finally { if (!signal.aborted) setBusy(null); }
  }

  function load(page = 0) {
    void perform("load", async signal => {
      const next = await libraryDiscussion(resourceId, page, signal);
      if (signal.aborted) return;
      setDiscussion(current => !current || page === 0 ? next : appendDiscussionPage(current, next));
    }, "Không tải được bình luận.");
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || !canInteract) return;
    void perform("post", async signal => {
      const comment = await commentOnLibrary(resourceId, body, signal);
      if (signal.aborted) return;
      setDraft("");
      setDiscussion(current => current && { ...current, commentCount: current.commentCount + 1, comments: [comment, ...current.comments] });
    }, "Chưa gửi được bình luận. Vui lòng thử lại.");
  }

  function toggleLike() {
    if (!canInteract || !discussion) return;
    void perform("like", async signal => {
      const reaction = await reactToLibrary(resourceId, !discussion.liked, signal);
      if (!signal.aborted) setDiscussion(current => current && { ...current, ...reaction });
    }, "Chưa cập nhật được lượt thích.");
  }

  function remove(commentId: string) {
    void perform(commentId, async signal => {
      await deleteLibraryComment(resourceId, commentId, signal);
      if (signal.aborted) return;
      setDiscussion(current => current && {
        ...current, commentCount: Math.max(0, current.commentCount - 1),
        comments: current.comments.filter(comment => comment.id !== commentId), hasMore: false,
      });
      // Reload the first page because deleting a row changes offset pagination.
      const next = await libraryDiscussion(resourceId, 0, signal);
      if (!signal.aborted) setDiscussion(next);
    }, "Không cập nhật được danh sách bình luận. Vui lòng thử lại.");
  }

  return <section className={styles.discussion} aria-label="Tương tác mô phỏng" aria-busy={Boolean(busy)}>
    <div className={styles.heading}>
      <h3><ChatBubbleIcon /> Bình luận {discussion && <Badge color="gray" variant="soft">{discussion.commentCount}</Badge>}</h3>
      {discussion && (canInteract
        ? <Button size="2" variant={discussion.liked ? "soft" : "outline"} color={discussion.liked ? "crimson" : "gray"} aria-pressed={discussion.liked} onClick={toggleLike} disabled={Boolean(busy)}>{busy === "like" ? <Spinner /> : discussion.liked ? <HeartFilledIcon /> : <HeartIcon />} {discussion.likes} Thích</Button>
        : <span className={styles.likes}><HeartIcon aria-hidden="true" /> {discussion.likes} lượt thích</span>)}
    </div>
    {canInteract && discussion && <form className={styles.composer} onSubmit={submit}>
      <TextArea aria-label="Nội dung bình luận" placeholder="Viết bình luận…" value={draft} onChange={event => setDraft(event.target.value)} maxLength={discussion.commentMaxLength} rows={3} resize="vertical" disabled={busy === "post"} />
      <div className={styles.composerActions}><span>{draft.length}/{discussion.commentMaxLength}</span><Button size="2" type="submit" disabled={!draft.trim() || draft.length > discussion.commentMaxLength || Boolean(busy)}>{busy === "post" ? <Spinner /> : <PaperPlaneIcon />} Gửi</Button></div>
    </form>}
    {error && <div className={styles.error} role="alert"><span>{error}</span><Button size="1" variant="ghost" color="gray" disabled={Boolean(busy)} onClick={() => load()}>Thử lại</Button></div>}
    {!discussion && busy === "load" && <div className={styles.loading}><Spinner size="2" /><span>Đang tải bình luận…</span></div>}
    {discussion && <>
      {discussion.comments.length === 0 && <p className={styles.empty}>Chưa có bình luận.</p>}
      <ul className={styles.comments}>{discussion.comments.map(comment => <li className={styles.comment} key={comment.id}>
        <Avatar size="2" radius="full" src={comment.avatarUrl || undefined} fallback={comment.authorName.trim().charAt(0).toUpperCase() || "U"} />
        <div className={styles.commentContent}><div className={styles.commentMeta}><strong>{comment.authorName}</strong><time dateTime={comment.createdAt}>{dateFormatter.format(new Date(comment.createdAt))}</time></div><p>{comment.body}</p></div>
        {user && comment.canDelete && <IconButton size="1" variant="ghost" color="gray" aria-label={`Xóa bình luận của ${comment.authorName}`} disabled={Boolean(busy)} onClick={() => remove(comment.id)}>{busy === comment.id ? <Spinner /> : <TrashIcon />}</IconButton>}
      </li>)}</ul>
      {discussion.hasMore && <div className={styles.more}><Button size="2" variant="soft" color="gray" disabled={Boolean(busy)} onClick={() => load(discussion.page + 1)}>{busy === "load" && <Spinner />} Xem thêm bình luận</Button></div>}
    </>}
  </section>;
}