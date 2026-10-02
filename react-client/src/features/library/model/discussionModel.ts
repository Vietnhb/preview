import type { LibraryDiscussion } from "../api/libraryApi";

export function appendDiscussionPage(current: LibraryDiscussion, incoming: LibraryDiscussion): LibraryDiscussion {
  if (incoming.page <= current.page) return current;
  return {
    ...current,
    page: incoming.page,
    hasMore: incoming.hasMore,
    comments: Array.from(new Map([...current.comments, ...incoming.comments].map(comment => [comment.id, comment])).values()),
  };
}