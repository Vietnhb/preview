import axiosClient from "./axios";
import type { LibraryFolder, LibraryItem } from "../types/physlive";

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
