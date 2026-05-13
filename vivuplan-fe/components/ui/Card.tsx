import type { HTMLAttributes, ReactNode } from "react";
import { clsx } from "clsx";

export function Card({
  children,
  hover = false,
  className,
  ...props
}: HTMLAttributes<HTMLDivElement> & { children: ReactNode; hover?: boolean }) {
  return (
    <div className={clsx("card", hover && "card-hover", className)} {...props}>
      {children}
    </div>
  );
}

