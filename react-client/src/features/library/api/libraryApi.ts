import axiosClient from "../../../shared/api/client";
import type { LibraryFolder, LibraryItem } from "../../../shared/types/physlive";

/**
 * API endpoints for library management
 * Handles personal library, shared library, folders, and library items
 */

export const saveLibrary = (
  simulationId: string, 
  folderId: string, 
  lessonId: string, 
  title: string, 
  visibility: LibraryItem["visibility"] = "PERSONAL"
) => 
  axiosClient.post<LibraryItem>("/library", { 
    simulationId, 
    folderId, 
    lessonId, 
    title, 
    visibility 
  }).then(r => r.data);

export const cloneSharedLibrary = (id: string, folderId: string, title?: string) =>
  axiosClient.post<LibraryItem>(`/library/${id}/clone`, { folderId, title }).then(r => r.data);

export const library = (topic?: string) => 
  axiosClient.get<LibraryItem[]>("/library", { params: { topic } }).then(r => r.data);

export const communityLibrary = (topic?: string) =>
  axiosClient.get<LibraryItem[]>("/library/community", { params: { topic } }).then(r => r.data);

export type LibraryComment = {
  id: string;
  authorId: number;
  authorName: string;
  avatarUrl: string | null;
  body: string;
  createdAt: string;
  canDelete: boolean;
};

export type LibraryDiscussion = {
  likes: number;
  liked: boolean;
  commentCount: number;
  commentMaxLength: number;
  comments: LibraryComment[];
  page: number;
  hasMore: boolean;
  canInteract: boolean;
};

export const libraryDiscussion = (id: string, page = 0, signal?: AbortSignal) =>
  axiosClient.get<LibraryDiscussion>(`/library/${id}/discussion`, { params: { page, size: 20 }, signal }).then(r => r.data);

export const commentOnLibrary = (id: string, body: string, signal?: AbortSignal) =>
  axiosClient.post<LibraryComment>(`/library/${id}/comments`, { body }, { signal }).then(r => r.data);

export const deleteLibraryComment = (id: string, commentId: string, signal?: AbortSignal) =>
  axiosClient.delete(`/library/${id}/comments/${commentId}`, { signal });

export const reactToLibrary = (id: string, liked: boolean, signal?: AbortSignal) =>
  axiosClient.put<Pick<LibraryDiscussion, "likes" | "liked">>(`/library/${id}/reaction`, { liked }, { signal }).then(r => r.data);

export const personalLibrary = () => 
  axiosClient.get<LibraryItem[]>("/library/mine").then(r => r.data);

export const moveLibraryItem = (id: string, folderId: string) => 
  axiosClient.patch<LibraryItem>(`/library/${id}/folder`, { folderId }).then(r => r.data);

export const renameLibraryItem = (id: string, title: string) => 
  axiosClient.patch<LibraryItem>(`/library/${id}`, { title }).then(r => r.data);

export const deleteLibraryItem = (id: string) => 
  axiosClient.delete(`/library/${id}`);

export const libraryFolders = () => 
  axiosClient.get<LibraryFolder[]>("/library/folders").then(r => r.data);

export const createLibraryFolder = (name: string) => 
  axiosClient.post<LibraryFolder>("/library/folders", { name }).then(r => r.data);

export const renameLibraryFolder = (id: string, name: string) => 
  axiosClient.patch<LibraryFolder>(`/library/folders/${id}`, { name }).then(r => r.data);

export const deleteLibraryFolder = (id: string) => 
  axiosClient.delete(`/library/folders/${id}`);