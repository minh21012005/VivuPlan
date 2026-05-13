import type { HTMLAttributes, ReactNode } from "react";
import { clsx } from "clsx";

type BadgeTone = "teal" | "blue" | "green" | "purple" | "gray" | "glass";

export function Badge({
  children,
  tone = "teal",
  className,
  ...props
}: HTMLAttributes<HTMLSpanElement> & { children: ReactNode; tone?: BadgeTone }) {
  return (
    <span className={clsx("badge", tone === "glass" ? "badge-glass" : `badge-${tone}`, className)} {...props}>
      {children}
    </span>
  );
}

