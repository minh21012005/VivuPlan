export function getTripShareUrl(shareCode: string) {
  const path = `/itinerary/${shareCode}`;
  if (typeof window === "undefined") return path;
  return `${window.location.origin}${path}`;
}

function isAppleTouchDevice() {
  if (typeof navigator === "undefined") return false;
  return /iPad|iPhone|iPod/.test(navigator.userAgent)
    || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
}

function copyTextWithSelection(text: string) {
  if (typeof document === "undefined") return false;

  const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const selection = window.getSelection();
  const previousRanges = selection
    ? Array.from({ length: selection.rangeCount }, (_, index) => selection.getRangeAt(index).cloneRange())
    : [];
  const textarea = document.createElement("textarea");

  textarea.value = text;
  textarea.readOnly = true;
  textarea.setAttribute("aria-hidden", "true");
  textarea.style.position = "fixed";
  textarea.style.top = "0";
  textarea.style.left = "0";
  textarea.style.width = "1px";
  textarea.style.height = "1px";
  textarea.style.padding = "0";
  textarea.style.border = "0";
  textarea.style.opacity = "0";
  textarea.style.fontSize = "16px";
  document.body.appendChild(textarea);

  let copied = false;
  try {
    textarea.focus({ preventScroll: true });
    textarea.select();
    textarea.setSelectionRange(0, text.length);
    copied = document.execCommand("copy");
  } catch {
    copied = false;
  } finally {
    document.body.removeChild(textarea);
    selection?.removeAllRanges();
    previousRanges.forEach((range) => selection?.addRange(range));
    activeElement?.focus({ preventScroll: true });
  }

  return copied;
}

export async function copyTextToClipboard(text: string) {
  // iOS may revoke clipboard permission as soon as an awaited request starts.
  // Run its selection-based copy synchronously inside the original tap event.
  if (isAppleTouchDevice() && copyTextWithSelection(text)) {
    return true;
  }

  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // Fall back below when clipboard permissions are unavailable.
    }
  }

  return copyTextWithSelection(text);
}
