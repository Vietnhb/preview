import { useRef, type ReactNode } from "react";
import { Dialog, IconButton, Flex } from "@radix-ui/themes";
import { ReviewerIcon } from "./ReviewerKit";

export function ReviewerDialog({ title, children, onClose, wide = false }: Readonly<{ title: string; children: ReactNode; onClose: () => void; wide?: boolean }>) {
  const previousFocus = useRef<HTMLElement | null>(null);
  return <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}><Dialog.Content size="3" maxWidth={wide ? "800px" : "560px"} maxHeight="calc(100dvh - 48px)" className="reviewer-academic reviewer-dialog-content" aria-describedby={undefined} onOpenAutoFocus={() => { previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null; }} onCloseAutoFocus={(event) => { if (previousFocus.current?.isConnected) { event.preventDefault(); previousFocus.current.focus(); } }}>
    <Flex justify="between" align="center" gap="3" mb="5"><Dialog.Title mb="0">{title}</Dialog.Title><Dialog.Close><IconButton type="button" variant="soft" color="gray" aria-label="Đóng hộp thoại"><ReviewerIcon name="close" size={18} /></IconButton></Dialog.Close></Flex>
    {children}
  </Dialog.Content></Dialog.Root>;
}
